# Cloudflare API (bushorat-api)

## Setup

1. Install deps:
   ```bash
   cd cloudflare && npm install
   ```
2. Login and create resources:
   ```bash
   npx wrangler login
   npx wrangler d1 create bushorat-db
   npx wrangler r2 bucket create bushorat-gallery
   ```
3. Put the D1 `database_id` into `wrangler.toml`.
4. Set secrets:
   ```bash
   npx wrangler secret put JWT_SECRET
   npx wrangler secret put ADMIN_EMAIL
   npx wrangler secret put ADMIN_PASSWORD
   ```
5. Set `GOOGLE_CLIENT_ID` in `wrangler.toml` to your Google **Web client ID** (same as Android `default_web_client_id`).
6. Migrate schema:
   ```bash
   npm run db:migrate
   ```
7. Deploy:
   ```bash
   npm run deploy
   ```

## Local dev

```bash
npm run db:migrate:local
npm run dev
```

## Android config

Set `CLOUDFLARE_API_BASE_URL` in `local.properties` or `app/build.gradle.kts` `buildConfigField`, e.g.:

```
https://bushorat-api.<your-subdomain>.workers.dev
```
