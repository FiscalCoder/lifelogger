import { createHash } from 'node:crypto';
import { promises as fs } from 'node:fs';
import path from 'node:path';
import { config as loadEnv } from 'dotenv';
import { Pool, PoolClient } from 'pg';

loadEnv({ path: path.resolve(process.cwd(), '../.env') });
loadEnv({ path: path.resolve(process.cwd(), '.env') });

type Migration = {
  id: string;
  path: string;
  sql: string;
  checksum: string;
};

type AppliedMigration = {
  id: string;
  checksum: string;
  applied_at: Date;
};

const MIGRATIONS_DIR = path.resolve(process.cwd(), 'src/db/migrations');
const MIGRATIONS_TABLE = 'schema_migrations';
const LOCK_KEY = 719_415_001;

function requireDatabaseUrl(): string {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    throw new Error('DATABASE_URL is required to run database migrations.');
  }
  return databaseUrl;
}

function redactDatabaseUrl(databaseUrl: string): string {
  try {
    const parsedUrl = new URL(databaseUrl);
    if (parsedUrl.password) {
      parsedUrl.password = '***';
    }
    return parsedUrl.toString();
  } catch {
    return '[configured DATABASE_URL]';
  }
}

function checksum(sql: string): string {
  return createHash('sha256').update(sql).digest('hex');
}

async function listMigrations(): Promise<Migration[]> {
  const entries = await fs.readdir(MIGRATIONS_DIR, { withFileTypes: true });
  const sqlFiles = entries
    .filter((entry) => entry.isFile() && entry.name.endsWith('.sql'))
    .map((entry) => entry.name)
    .sort();

  return Promise.all(
    sqlFiles.map(async (fileName) => {
      const migrationPath = path.join(MIGRATIONS_DIR, fileName);
      const sql = await fs.readFile(migrationPath, 'utf8');
      return {
        id: fileName,
        path: migrationPath,
        sql,
        checksum: checksum(sql),
      };
    }),
  );
}

async function ensureMigrationsTable(client: PoolClient): Promise<void> {
  await client.query(`
    CREATE TABLE IF NOT EXISTS ${MIGRATIONS_TABLE} (
      id TEXT PRIMARY KEY,
      checksum TEXT NOT NULL,
      applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
  `);
}

async function loadAppliedMigrations(
  client: PoolClient,
): Promise<Map<string, AppliedMigration>> {
  const tableExists = await client.query<{ exists: string | null }>(
    `SELECT to_regclass('public.${MIGRATIONS_TABLE}')::text AS exists;`,
  );

  if (!tableExists.rows[0]?.exists) {
    return new Map();
  }

  const result = await client.query<AppliedMigration>(
    `SELECT id, checksum, applied_at FROM ${MIGRATIONS_TABLE} ORDER BY id;`,
  );

  return new Map(result.rows.map((row) => [row.id, row]));
}

async function applyMigration(
  client: PoolClient,
  migration: Migration,
  applied: AppliedMigration | undefined,
): Promise<'applied' | 'skipped'> {
  if (applied) {
    if (applied.checksum !== migration.checksum) {
      throw new Error(
        `Applied migration ${migration.id} has changed on disk. ` +
          'Create a new migration instead of editing an applied one.',
      );
    }
    return 'skipped';
  }

  await client.query('BEGIN');
  try {
    await client.query(migration.sql);
    await client.query(
      `INSERT INTO ${MIGRATIONS_TABLE} (id, checksum) VALUES ($1, $2);`,
      [migration.id, migration.checksum],
    );
    await client.query('COMMIT');
    return 'applied';
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  }
}

async function printStatus(client: PoolClient, migrations: Migration[]): Promise<void> {
  const appliedMigrations = await loadAppliedMigrations(client);

  for (const migration of migrations) {
    const applied = appliedMigrations.get(migration.id);
    if (!applied) {
      console.log(`pending  ${migration.id}`);
    } else if (applied.checksum !== migration.checksum) {
      console.log(`changed  ${migration.id}`);
    } else {
      console.log(`applied  ${migration.id}`);
    }
  }
}

async function run(): Promise<void> {
  const mode = process.argv[2] ?? 'migrate';
  const databaseUrl = requireDatabaseUrl();
  const migrations = await listMigrations();
  const pool = new Pool({ connectionString: databaseUrl, max: 1 });

  console.log(`[db] ${mode} using ${redactDatabaseUrl(databaseUrl)}`);

  const client = await pool.connect();
  try {
    if (mode === 'status' || mode === 'check') {
      await printStatus(client, migrations);
      const appliedMigrations = await loadAppliedMigrations(client);
      const hasPendingOrChanged = migrations.some((migration) => {
        const applied = appliedMigrations.get(migration.id);
        return !applied || applied.checksum !== migration.checksum;
      });
      if (mode === 'check' && hasPendingOrChanged) {
        process.exitCode = 1;
      }
      return;
    }

    if (mode !== 'migrate' && mode !== 'init') {
      throw new Error(`Unknown migration command: ${mode}`);
    }

    await client.query('SELECT pg_advisory_lock($1);', [LOCK_KEY]);
    try {
      await ensureMigrationsTable(client);
      const appliedMigrations = await loadAppliedMigrations(client);

      for (const migration of migrations) {
        const result = await applyMigration(
          client,
          migration,
          appliedMigrations.get(migration.id),
        );
        console.log(`${result.padEnd(7)} ${migration.id}`);
      }
    } finally {
      await client.query('SELECT pg_advisory_unlock($1);', [LOCK_KEY]);
    }
  } finally {
    client.release();
    await pool.end();
  }
}

run().catch((error) => {
  console.error('[db] migration failed:', error instanceof Error ? error.message : error);
  process.exitCode = 1;
});
