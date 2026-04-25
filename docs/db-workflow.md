# Database Workflow

LifeLogger uses the cloud PostgreSQL database from `backend/.env`.

The single required DB setting is:

```env
DATABASE_URL="postgres://lifelogger:<URL_ENCODED_PASSWORD>@lifelogger.c3ueqi6k0dik.ap-south-1.rds.amazonaws.com:5432/lifelogger?sslmode=require"
```

## First Time Cloud DB Init

For a fresh RDS database, init creates all tables and required extensions from
the first migration:

```bash
cd backend/api
npm ci
npm run db:migrate:status
npm run db:init
npm run db:migrate:status
```

This applies `src/db/migrations/0001_init.sql`, which enables `pgvector`, enables UUID helpers, creates the application tables, creates vector indexes, and creates normal lookup indexes.

Expected status before first init:

```text
pending  0001_init.sql
```

Expected status after init:

```text
applied  0001_init.sql
```

To run the same init from Docker:

```bash
cd backend
docker compose --profile tools run --rm migrate
```

## Checking Migration State

```bash
cd backend/api
npm run db:migrate:status
```

Use the check command in deployment or CI when you want a non-zero exit code if there are pending or changed migrations:

```bash
npm run db:migrate:check
```

## Source Of Truth

`backend/api/src/db/schema.ts` is the source of truth for application table and
column structure.

Use SQL migrations as the applied history of those schema changes. Do not edit a
table directly in RDS and then try to make code match it later.

There are a few things Drizzle cannot fully represent for this project:

- PostgreSQL extensions such as `vector`, `"uuid-ossp"`, and `pgcrypto`
- pgvector index details such as `ivfflat` and `vector_cosine_ops`
- repeatable/versioned data inserts

Keep those in reviewed SQL migrations, but keep table and column definitions in
`schema.ts` first.

## Schema Changes

For a table or column change, edit the source-of-truth schema first:

1. Update `src/db/schema.ts`.
2. Generate a migration:

```bash
cd backend/api
npm run db:generate -- --name=add_device_table
```

Then review the generated SQL in `src/db/migrations`, run
`npm run db:migrate:status`, run `npm run db:migrate`, and restart the API and
pipeline after the migration succeeds.

For changes that Drizzle cannot generate, create a custom migration:

```bash
npm run db:new -- --name=add_vector_index
```

Then edit the generated SQL file.

Do not edit a migration file after it has been applied to RDS. Add a follow-up migration instead.

Verify the tables and extensions:

```bash
cd ../
set -a
source .env
set +a
psql "$DATABASE_URL" -c "
SELECT extname FROM pg_extension WHERE extname IN ('vector', 'uuid-ossp', 'pgcrypto') ORDER BY extname;
SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name;
"
```

You should see these application tables:

- `schema_migrations`
- `speaker_profiles`
- `transcript_segments`
- `unknown_speakers`
- `upload_queue`

## Generated Migration Baseline

`src/db/migrations/meta/0001_snapshot.json` is the Drizzle snapshot for the
current `schema.ts` table structure. It is intentionally paired with the manual
`0001_init.sql` migration, so the next generated schema migration starts at
`0002_*` and does not recreate the initial tables.

Do not delete or hand-edit files under `src/db/migrations/meta`.

## Versioned Data Changes

For data that must exist in every environment, use a custom migration:

```bash
npm run db:new -- --name=seed_default_speaker
```

Write idempotent SQL when possible:

```sql
INSERT INTO speaker_profiles (name)
VALUES ('Bharghav')
ON CONFLICT (name) DO NOTHING;
```

For one-off investigation or repair data, use `psql` directly with a transaction:

```bash
cd backend
set -a
source .env
set +a
psql "$DATABASE_URL"
```

```sql
BEGIN;
-- inspect first
SELECT COUNT(*) FROM transcript_segments;
-- make the intended change
COMMIT;
```

Do not commit one-off repair SQL unless it needs to be repeatable.

The migration runner wraps each migration file in a transaction. Do not put
`BEGIN` or `COMMIT` in migration files. If a PostgreSQL operation cannot run in
a transaction, such as `CREATE INDEX CONCURRENTLY`, handle that as a separate
manual maintenance step and document it in the migration file.

## Deployment Order

Always apply DB migrations before deploying code that depends on them:

1. Pull the latest code on the VPS.
2. Confirm `backend/.env` has the RDS `DATABASE_URL`.
3. Run `npm run db:migrate:status`.
4. Run `npm run db:migrate` or the Docker `migrate` service.
5. Deploy or restart `api` and `pipeline`.
