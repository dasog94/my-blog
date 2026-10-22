# Jimmy's Blog

The frontend and backend are maintained as independent projects.

- `front/` — Next.js frontend built with Vinext/Vite
- `backend/` — Ktor authentication API
- `docs/` — Project documentation

Detailed setup instructions are available in [docs/frontend.md](docs/frontend.md).
For backend hosting, see [Deploy on Render](docs/render.md).

[Deploy backend to Render](https://render.com/deploy?repo=https://github.com/dasog94/my-blog)

## Development

Run the frontend from `front/` and the API from `backend/`.

```bash
cd front
npm ci
npm run dev
```

```bash
cd backend
set -a
source .env
set +a
gradle run
```

Copy `backend/.env.example` to `backend/.env` and replace its credential
placeholders first. Local development uses `APP_ENV=development`; the API
otherwise defaults to production validation. Java 21 and Gradle 8.14.3 are
required for the backend.

Production builds require the actual HTTPS API origin in
`NEXT_PUBLIC_API_URL`, and the production API requires the frontend HTTPS
origin(s) in `CORS_ALLOWED_ORIGINS`. See the [deployment guide](docs/render.md)
for existing Render service migration and the local/deployed login tests.
