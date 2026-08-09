# Frontend

The blog frontend lives in `front/` and uses Next.js through Vinext.

## Requirements

- Node.js 22.13 or newer
- pnpm
- Ktor API available at `http://localhost:8080`

## Development

```bash
cd front
pnpm install
pnpm dev
```

The frontend is served at `http://localhost:3000`.

## Configuration

Copy `front/.env.example` to `front/.env.local` when overriding the API URL:

```text
NEXT_PUBLIC_API_URL=http://localhost:8080
```

## Authentication

The login page calls the Ktor authentication API. The returned JWT and user
profile are stored in browser `sessionStorage` for the lifetime of the tab.

Local credentials are configured through the backend environment variables
documented in [features/login.md](features/login.md).

## Build

```bash
cd front
pnpm build
```
