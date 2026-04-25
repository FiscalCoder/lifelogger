# Database Migrations

SQL files in this folder are applied in filename order by `npm run db:migrate`.

Rules:

- Do not edit a migration after it has been applied to any shared database.
- Treat `src/db/schema.ts` as the source of truth for table and column structure.
- Generate a new migration for every schema change.
- Use custom migrations for versioned data, extensions, and index details Drizzle cannot express.
- Review generated or handwritten SQL before applying it to RDS.

Common commands:

```bash
npm run db:generate -- --name=add_example_table
npm run db:new -- --name=seed_example_data
npm run db:migrate:status
npm run db:migrate
```
