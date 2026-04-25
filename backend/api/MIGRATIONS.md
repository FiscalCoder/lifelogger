# Migrations

`src/db/schema.ts` is the source of truth for application table and column
structure. SQL files under `src/db/migrations` are the reviewed migration
history that gets applied to RDS.

## First Time Init

Run this once against a fresh RDS database:

```bash
cd backend/api
npm ci
npm run db:migrate:status
npm run db:init
npm run db:migrate:status
```

Before init, `0001_init.sql` should show as `pending`. After init, it should show
as `applied`.

Verify the database:

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

Expected application tables:

- `schema_migrations`
- `speaker_profiles`
- `transcript_segments`
- `unknown_speakers`
- `upload_queue`

## Schema Changes

For table or column changes:

```bash
cd backend/api
# edit src/db/schema.ts first
npm run db:generate -- --name=add_device_table
npm run db:migrate:status
npm run db:migrate
```

Review generated SQL before applying it. Add manual SQL where Drizzle cannot
represent the change, such as PostgreSQL extensions, pgvector indexes, partial
indexes, or versioned data inserts.

## Custom SQL Or Versioned Data

For changes that are not table/column structure:

```bash
cd backend/api
npm run db:new -- --name=seed_default_data
# edit the generated SQL file
npm run db:migrate
```

Keep migrations forward-only. Do not edit a migration after it has been applied
to RDS; add a new migration instead.
