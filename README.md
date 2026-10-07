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
# Fill S3_* with your AWS bucket, region, IAM keys, and public/CDN base URL
docker compose up -d postgres redis
./mvnw spring-boot:run
```

Redis holds ephemeral presence sessions and presence transition pub/sub. Without
Redis, set `PRESENCE_STORE=memory` for single-instance local use only. For Redis
Cloud, set `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, usually
`REDIS_USERNAME=default`, and `REDIS_SSL=true` when the endpoint requires TLS.

Spring Boot imports `backend/.env` automatically (see `spring.config.import` in
`application.properties`). You can still `set -a && source .env && set +a` to
export the same values into the shell before other tools.

Create the AWS S3 bucket in the console and configure public-read for public
prefixes plus browser CORS for `FRONTEND_URL` before running the app. Features
share the bucket via key prefixes (e.g. `avatars/*`, `group-avatars/*`). Set `S3_PUBLIC_BASE_URL`
to the bucket URL or CloudFront base.

User and group avatars are loaded in the browser via public object URLs. Message
attachments stay private (presigned GET). For avatar images to display, allow
anonymous `s3:GetObject` on those prefixes only (Bucket policy), after relaxing
Block Public Access enough to permit that policy:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "PublicReadAvatars",
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": [
        "arn:aws:s3:::YOUR_BUCKET_NAME/avatars/*",
        "arn:aws:s3:::YOUR_BUCKET_NAME/group-avatars/*"
      ]
    }
  ]
}
```

Do not make `message-attachments/*` public.

### Rate limiting

All rate limits share `common/ratelimit`. Endpoints opt in with
`@RateLimited(RateLimitPolicy.X)`; exceeding the limit returns `429` with a
`Retry-After` header. Typing events over WebSocket are dropped silently.
The window is `RATE_LIMIT_WINDOW` (default `1m`) and the maximum hits per
window for each policy is `RATE_LIMIT_<POLICY>_MAX_HITS` in `.env`
(see `.env.example`). Auth endpoints are keyed by client IP; all others by the
authenticated user. Counters are in memory and reset on restart. Behind a
reverse proxy, configure `server.forward-headers-strategy` so the real client
IP is used.

Object storage is AWS S3 (optional CloudFront). The bucket must allow browser
PUT from `FRONTEND_URL` and public or CDN reads for public prefixes such as
`avatars/*` and `group-avatars/*`. Avatar size limit is `AVATAR_MAX_BYTES` (default 2MB).

Browser uploads use presigned PUT with a signed `Content-Type` header. Configure
bucket CORS for your frontend origin or OPTIONS/PUT will return **403**:

```json
[
  {
    "AllowedOrigins": ["http://localhost:3000"],
    "AllowedMethods": ["GET", "PUT", "HEAD"],
    "AllowedHeaders": ["Content-Type"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3000
  }
]
```

Replace the origin with `FRONTEND_URL` in non-local environments. The IAM user
behind `S3_ACCESS_KEY` / `S3_SECRET_KEY` needs object access on the app key
prefixes (`avatars/*`, `group-avatars/*`, `message-attachments/*`). HeadObject
uses `s3:GetObject` (there is no separate `s3:HeadObject` action):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "SenderObjectPrefixes",
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject"
      ],
      "Resource": [
        "arn:aws:s3:::YOUR_BUCKET_NAME/avatars/*",
        "arn:aws:s3:::YOUR_BUCKET_NAME/group-avatars/*",
        "arn:aws:s3:::YOUR_BUCKET_NAME/message-attachments/*"
      ]
    }
  ]
}
```

The application connects to `localhost:5432/sender` by default. Override the
connection with `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`.
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
