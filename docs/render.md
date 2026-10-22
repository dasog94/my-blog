# Deploy the frontend and Ktor API

The repository's `render.yaml` defines a Docker web service for `backend/`.
The frontend is a Next.js application built with **Vinext/Vite** in `front/`
and is deployed separately. No live frontend origin, API origin, or successful
deployment is recorded here. Use the addresses assigned by the hosting
providers; do not derive an API hostname from the Render service name.

## Environment contract

An **origin** is a scheme, hostname, and optional port. It does not include
`/login`, `/api`, a query string, or a fragment. A single trailing `/` is
accepted and normalized. The frontend calls `/api/auth/login` and
`/api/auth/me` relative to the configured API origin.

| Variable | Where and when | Production value |
| --- | --- | --- |
| `NEXT_PUBLIC_API_URL` | Frontend build environment | The actual, browser-accessible **HTTPS API origin**. Required for every production build. |
| `APP_ENV` | Ktor process startup | `production`, set by the Blueprint and also the backend default when omitted. Only `production` and `development` are accepted. |
| `CORS_ALLOWED_ORIGINS` | Ktor process startup | The actual **frontend HTTPS origin(s)**, separated by commas. Required in production. |
| `JWT_SECRET` | Ktor process startup | A secret supplied by Render's `generateValue`, or another protected environment setting. |
| `BLOG_ACCOUNT_ID`, `BLOG_ACCOUNT_NAME`, `BLOG_ACCOUNT_EMAIL`, `BLOG_ACCOUNT_PASSWORD` | Ktor process startup | All must be nonblank. Configure the single account used by the login form. |

Production validation rejects HTTP, localhost/loopback/unspecified addresses,
wildcards, credentials in URLs, invalid ports, paths, queries, and fragments.
CORS lists cannot contain empty entries. It fails before the frontend build
or API startup completes, with the failing variable's name. Only explicit
local development allows the localhost defaults.

The API URL belongs to the **API**, while the CORS list contains the
**frontend**. Include every actual frontend origin that should be allowed,
including a preview origin only when intended. Use exact origins, not `*`,
URL patterns, or the name of a Render internal service.

Validation checks the values' format and deployment suitability. It cannot
prove a hostname is the intended service, that DNS/TLS works, or that the two
deployments are compatible. The browser tests below check the actual login
connection once addresses and a test account are available.

## Deploy a new service

1. Obtain the actual frontend HTTPS origin from its hosting provider. If it
   is not assigned yet, resolve that first instead of inventing a hostname.
2. After the reviewed changes are merged to `main`, open the
   [Render Dashboard](https://dashboard.render.com), choose **New → Blueprint**,
   connect `dasog94/my-blog`, and select `main` and `render.yaml`. The service
   branch in the Blueprint is explicitly `main`.
3. Enter `BLOG_ACCOUNT_EMAIL`, `BLOG_ACCOUNT_PASSWORD`, and
   `CORS_ALLOWED_ORIGINS` when prompted. The last value is the frontend
   origin from step 1. Keep credentials in Render's settings.
4. Verify `APP_ENV=production` and the intended plan, then deploy. The
   Blueprint currently specifies the Free plan. `rootDir: backend` makes
   the Dockerfile path and build context relative to `backend/`.
5. Copy the actual HTTPS API origin shown by Render. Its `/health` endpoint
   should return `ok`. Ktor binds to `0.0.0.0` and Render's `PORT` (8080 when
   `PORT` is absent). Render terminates public HTTPS; the process still
   listens on its internal HTTP port.
6. Set `NEXT_PUBLIC_API_URL` to that API origin in the **frontend build
   environment**, then build and deploy the frontend.
7. Run the deployed browser tests below using both confirmed origins.

`NEXT_PUBLIC_API_URL` is public and is embedded in browser JavaScript at
build time. Changing only the running frontend's environment does not update
an existing bundle: **rebuild and redeploy** it. Never put passwords or the JWT
signing secret in a `NEXT_PUBLIC_*` variable.

The Vite configuration validates the same value it embeds in the client.
Shell/hosting environment variables override `.env` files; mode-specific
files override generic files. A leftover `front/.env.local` containing
localhost will make a production build fail unless overridden. A custom
build mode still receives production validation.

## Upgrade an existing Render service

Before deploying these validation changes, set the existing service's
`CORS_ALLOWED_ORIGINS` to its confirmed frontend HTTPS origin(s), and check
the frontend build's `NEXT_PUBLIC_API_URL`. The previous localhost default
will now stop production API startup.

**Changing the Blueprint to `sync: false` does not replace the old value on
an existing service.** Render only prompts for these values during initial
Blueprint creation, and ignores them on subsequent Blueprint updates. Edit
the environment setting on the existing Render service manually and redeploy.
Preview environments also need their own explicitly supplied value. Keep
`APP_ENV=production`; switching to development is not a production fix.

## Local development and Docker verification

Use Node.js 22.13+ for the frontend, and Java 21 / Gradle 8.14.3 for the API
(or Docker). Copy `backend/.env.example` to `backend/.env`, replace the
credential placeholders, and keep `APP_ENV=development`. Ktor does not read
`.env` files automatically; load the file into the process environment.

From the repository root:

```sh
docker build -t jimmy-blog-api backend
docker run --rm --env-file backend/.env -p 8080:8080 jimmy-blog-api
```

Or run without Docker:

```sh
set -a
. backend/.env
set +a
gradle -p backend run
```

In another terminal:

```sh
cd front
npm ci
npm run dev
```

The development defaults are frontend `http://localhost:3000` and API
`http://localhost:8080`. If using `127.0.0.1` or another port, update both
the frontend API URL and the backend's exact allowed frontend origin.
Do not include a local HTTP frontend in a production CORS list; use a local
API for development or an explicitly configured HTTPS preview frontend.

## Automated verification

The deployment-validation workflow runs on pull requests and pushes to
`main`. It uses local fixture credentials and a reserved `.test` origin
for build-only checks; these are not live deployment addresses.

Run the focused tests locally:

```sh
gradle -p backend test installDist
cd front
npm ci
npm run test:unit
npm run test:config
npx playwright install chromium
npm run test:e2e
```

The backend tests cover startup validation, JSON login preflight, successful
and rejected logins, bearer-token authentication, and allowed/disallowed
origins. Frontend tests cover URL validation and authentication requests;
`test:config` also proves the actual build command rejects missing, blank,
and local API origins before producing a bundle.
Playwright starts the real frontend and Ktor API on separate local ports,
then checks browser login, session storage, the authenticated header,
`Authorization` requests to `/api/auth/me`, readable authentication errors,
and browser enforcement of a disallowed origin. Existing services on the
test ports must be stopped first; the suite does not reuse arbitrary servers.

For the production build and rendered-HTML tests, supply the confirmed API
origin as `NEXT_PUBLIC_API_URL` and run `npm test` from `front/`. If checking
only the build offline, an explicitly provided reserved HTTPS `.test` origin
can be used as a fixture; never deploy that test bundle.

### Verify a deployed frontend and API

Set these environment variables through a private local environment or a
protected CI job, using actual confirmed values:

| Variable | Value |
| --- | --- |
| `E2E_FRONTEND_URL` | The final HTTPS frontend origin (after any redirects). |
| `E2E_API_URL` | The HTTPS API origin expected in the deployed frontend bundle. |
| `E2E_EMAIL` | The configured test account's email. |
| `E2E_PASSWORD` | That account's password, accepted by the login form. |

Then, from `front/`:

```sh
npm run test:e2e:deployed
```

This mode requires every value, starts no local servers, and uses the actual
browser UI. It checks that the bundle sends login to the expected API, that
the browser can read login and `/me` responses through CORS, and that invalid
credentials return the expected error. It deliberately attempts one failed
login. Use an account appropriate for testing and account for any external
rate limiting. Traces, screenshots, and videos are disabled so this suite
does not save credentials or session tokens in those artifacts.

The deployed suite is not run automatically by the repository workflow.
Until the real addresses and account are supplied and the suite passes,
local tests do not establish that the production deployment works.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Build names `NEXT_PUBLIC_API_URL` as invalid | Supply the actual HTTPS API origin in the build environment; check `.env.local` and rebuild. |
| API startup names `CORS_ALLOWED_ORIGINS` as invalid | Set exact frontend HTTPS origins on the existing Render service, then redeploy. |
| `/health` works but browser login fails | Inspect the browser's request destination and `OPTIONS` preflight. Health checks do not validate CORS or credentials. |
| Browser requests localhost or the old API | Rebuild/redeploy the frontend with the corrected public variable; a runtime-only change is insufficient. |
| Preflight fails or lacks `Access-Control-Allow-Origin` | Compare the browser's `Origin` with the API allowlist, including scheme and non-default port. Allow `Content-Type` for login and `Authorization` for `/me`. |
| Login returns readable `401` | The API was reached; check the configured account email/password. |
| First request is slow | A Render Free service may be waking from idle. Warm `/health` and allow startup time before diagnosing an origin mismatch. |

Render Free web services currently spin down after 15 minutes without
traffic; waking takes about a minute. Their local filesystem is ephemeral.
The Docker image caps the JVM heap at 256 MB. These hosting characteristics
are separate from API URL and CORS validation.

References: [Render Blueprint environment variables](https://render.com/docs/blueprint-spec#setting-environment-variables),
[Render monorepos](https://render.com/docs/monorepo-support),
[Render Free services](https://render.com/docs/free),
[Next.js public variables](https://nextjs.org/docs/app/guides/environment-variables),
[Vite env loading](https://vite.dev/guide/env-and-mode), and
[Ktor CORS](https://ktor.io/docs/server-cors.html).
