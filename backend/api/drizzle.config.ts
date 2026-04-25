import 'dotenv/config';
import { defineConfig } from 'drizzle-kit';
import { config as loadEnv } from 'dotenv';

loadEnv({ path: '../.env' });

export default defineConfig({
  dialect: 'postgresql',
  schema: './src/db/schema.ts',
  out: './src/db/migrations',
  dbCredentials: {
    url: process.env.DATABASE_URL ?? '',
  },
  migrations: {
    table: 'schema_migrations',
    schema: 'public',
  },
  strict: true,
  verbose: true,
});
