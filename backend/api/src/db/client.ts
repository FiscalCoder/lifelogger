import { drizzle } from 'drizzle-orm/node-postgres';
import { Pool } from 'pg';
import * as schema from './schema';

type DatabaseLogConfig = {
  readonly user: string;
  readonly host: string;
  readonly port: string;
  readonly database: string;
  readonly sslmode: string;
};

function readDatabaseLogConfig(): DatabaseLogConfig {
  const rawUrl = process.env.DATABASE_URL ?? '';
  try {
    const url = new URL(rawUrl.replace(/^['"]|['"]$/g, ''));
    return {
      user: url.username || 'unknown',
      host: url.hostname || 'unknown',
      port: url.port || '5432',
      database: url.pathname.replace(/^\//, '') || 'unknown',
      sslmode: url.searchParams.get('sslmode') ?? 'not-set',
    };
  } catch {
    return {
      user: 'unparseable',
      host: 'unparseable',
      port: 'unparseable',
      database: 'unparseable',
      sslmode: 'unparseable',
    };
  }
}

const databaseLogConfig = readDatabaseLogConfig();

console.log(
  `[db] Config user=${databaseLogConfig.user} host=${databaseLogConfig.host} `
  + `port=${databaseLogConfig.port} database=${databaseLogConfig.database} `
  + `sslmode=${databaseLogConfig.sslmode} `
  + `nodeExtraCaCerts=${process.env.NODE_EXTRA_CA_CERTS ? 'configured' : 'not-configured'}`,
);

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  max: 10,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
});

pool.on('connect', () => {
  console.log(
    `[db] Pool connection established host=${databaseLogConfig.host} `
    + `database=${databaseLogConfig.database} user=${databaseLogConfig.user}`,
  );
});

pool.on('error', (err) => {
  console.error('[db] Unexpected pool error', {
    message: err.message,
    stack: err.stack,
  });
});

export const db = drizzle(pool, { schema });

export async function verifyDatabaseConnection(): Promise<void> {
  const start = Date.now();
  try {
    const result = await pool.query<{
      current_user: string;
      current_database: string;
      server_version: string;
      server_addr: string | null;
      server_port: number | null;
    }>(
      `SELECT current_user,
              current_database(),
              current_setting('server_version') AS server_version,
              inet_server_addr()::text AS server_addr,
              inet_server_port() AS server_port`,
    );
    const row = result.rows[0];
    console.log(
      `[db] Startup connection ok user=${row.current_user} `
      + `database=${row.current_database} serverVersion=${row.server_version} `
      + `server=${row.server_addr ?? 'unknown'}:${row.server_port ?? 'unknown'} `
      + `duration=${Date.now() - start}ms`,
    );
  } catch (err) {
    console.error(
      `[db] Startup connection failed host=${databaseLogConfig.host} `
      + `database=${databaseLogConfig.database} user=${databaseLogConfig.user} `
      + `duration=${Date.now() - start}ms`,
      err,
    );
  }
}
