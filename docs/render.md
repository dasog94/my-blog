# Deploy the Ktor backend on Render

The repository's `render.yaml` creates a **free** Docker web service from
`backend/`. Render generates the JWT secret and checks `/health` for readiness.
Ktor listens on `0.0.0.0` and Render's `PORT`, falling back to 8080 locally.

## Deploy

1. Push the deployment files to `main` on `dasog94/my-blog`.
2. Sign in at https://dashboard.render.com and choose **New → Blueprint**.
3. Connect `dasog94/my-blog`, select `main`, and use `render.yaml`.
4. Enter `BLOG_ACCOUNT_EMAIL` and a strong `BLOG_ACCOUNT_PASSWORD` in Render.
   These are the credentials you will use in the blog login form.
5. Confirm the service uses the **Free** plan and deploy the Blueprint.
6. Copy the HTTPS service URL shown by Render. Opening `<service-url>/health`
   should return `ok` once deployment succeeds.

Keep credentials in Render's environment settings, never in the repository.
The API supports one account configured through environment variables.

## Connect the frontend

Set `NEXT_PUBLIC_API_URL` to the actual Render HTTPS service URL, without a
trailing slash, in `front/.env.local` for local development or in the frontend
hosting environment. Restart local development or rebuild/redeploy the hosted
frontend after changing this value.

The Blueprint initially allows `http://localhost:3000` so the local frontend
can use the deployed API. When the frontend is hosted, set the backend's
`CORS_ALLOWED_ORIGINS` to its exact origin, for example
`https://your-blog.example`. Multiple origins can be separated by commas.
Origins include the scheme and optional port but no page path. Do not use `*`.

## Local Docker verification

Create `backend/.env` from `backend/.env.example` and fill in credentials first.
From the repository root:

```sh
docker build -t jimmy-blog-api backend
docker run --rm --env-file backend/.env -p 8080:8080 jimmy-blog-api
```

Stop the existing local API first if it already uses port 8080.

## Free-plan behavior

Render free web services sleep after 15 minutes without traffic. The first
request after sleep can take about a minute, so the first login may be slow.
Local filesystem changes do not survive restarts; this API does not currently
require persistent storage. The JVM heap is capped at 256 MB in the image,
leaving room for JVM and networking overhead.

References: https://render.com/docs/free and https://render.com/docs/blueprint-spec.
