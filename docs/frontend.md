# Frontend

The blog frontend lives in `front/` and uses Next.js through Vinext.

## Requirements

- Node.js 22.13 or newer
- npm (the CI commands below use the committed `package-lock.json`)
- Ktor API available at `http://localhost:8080`

## Development

```bash
cd front
npm ci
npm run dev
```

The frontend is served at `http://localhost:3000`.

## Configuration

Copy `front/.env.example` to `front/.env.local` when overriding the local API URL:

```text
NEXT_PUBLIC_API_URL=http://localhost:8080
```

The localhost fallback is available only in development. Every production
build requires an explicit HTTPS API origin and rejects local addresses,
credentials, paths, queries, and fragments. The Vite configuration validates
the value before building and embeds it in the browser bundle. A trailing
slash is normalized. The API itself must allow the frontend's exact origin
through `CORS_ALLOWED_ORIGINS`.

Set the actual API origin in the hosting provider's **build environment**;
changing it later requires a rebuild/redeploy. `.env.local` also participates
in builds, so override a local value before building for production. See
[the deployment guide](render.md) for the complete environment contract and
existing Render service migration steps.

## Authentication

The login page calls the Ktor authentication API. The returned JWT and user
profile are stored in browser `sessionStorage` for the lifetime of the tab.

Local credentials are configured through the backend environment variables
documented in [features/login.md](features/login.md).

## Build

```bash
cd front
npm run build
```

Supply `NEXT_PUBLIC_API_URL` before running this command. No production
hostname is assumed by this repository.

## Tests

```sh
npm run test:unit
npm run test:config
npm test
```

`npm test` also builds and checks rendered HTML, so it requires a valid
production API origin. For browser login tests against the real local Ktor
API, build the backend with `gradle -p backend installDist` from the repository
root, install Chromium with `npx playwright install chromium` in `front/`,
then run `npm run test:e2e`. See [the deployment guide](render.md#automated-verification)
for requirements and the opt-in deployed login test.
