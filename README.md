# Sender backend

## Database workflow

PostgreSQL is the durable source of truth. Flyway applies versioned migrations
from `src/main/resources/db/migration` before Hibernate validates the mapped
entities. Do not use `spring.jpa.hibernate.ddl-auto=update` or edit an applied
migration. Schema changes must be added as a new migration, for example
`V2__add_conversations.sql`.

### First-time local setup

```bash
cp .env.example .env
docker compose up -d postgres
set -a
source .env
set +a
./mvnw spring-boot:run
```

Spring Boot does not load `.env` automatically, so the `source` step exports
the values into the application process. The application connects to
`localhost:5432/sender` by default. Override the connection with
`DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`.
Flyway creates and records the schema in its `flyway_schema_history` table
automatically during application startup.

If a database already contains the schema created by the old Hibernate
`update` mode, back it up and perform a one-time baseline only after checking
that it matches `V1`:

```bash
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true \
SPRING_FLYWAY_BASELINE_VERSION=1 \
./mvnw spring-boot:run
```

Do not use this for a new or partially initialized database; Flyway must run
`V1` there.

### Applying a migration

1. Create a new numbered SQL file under `src/main/resources/db/migration`.
2. Test it against a fresh database and a database containing the previous
   migrations.
3. Deploy the application; Flyway runs pending migrations in version order.
4. Never modify a migration that has already been applied. Create a corrective
   migration instead.


To recreate the local database, stop the stack and remove only its named
volume:

```bash
docker compose down
docker volume rm backend_sender-postgres-data
docker compose up -d postgres
```

The volume name may be prefixed by the Compose project name; use
`docker volume ls` to identify it before removal.
