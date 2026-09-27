# Jimmy's Blog

The frontend and backend are maintained as independent projects.

- `front/` — Next.js frontend
- `backend/` — Ktor authentication API
- `docs/` — Project documentation

Detailed setup instructions are available in [docs/frontend.md](docs/frontend.md).
For backend hosting, see [Deploy on Render](docs/render.md).

[Deploy backend to Render](https://render.com/deploy?repo=https://github.com/dasog94/my-blog)

## Development

Run the frontend from `front/` and the API from `backend/`.

```bash
cd front
pnpm dev
```

```bash
cd backend
set -a
source .env
set +a
gradle run
```
