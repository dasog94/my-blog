# Login

## Overview

The login feature authenticates a user through the Ktor API and maintains the
resulting browser session in the Next.js frontend.

## User flow

1. The user opens `/login`.
2. The user enters an email address and password.
3. The frontend sends the credentials to `POST /api/auth/login`.
4. The Ktor API validates the credentials and returns a JWT and user profile.
5. The frontend saves the response in browser `sessionStorage`.
6. The user returns to the homepage, where the header displays their account.
7. Logging out removes the session from `sessionStorage`.

## Backend account configuration

```text
JWT_SECRET=replace-with-a-long-random-secret
BLOG_ACCOUNT_ID=usr_jimmy
BLOG_ACCOUNT_NAME=Jimmy
BLOG_ACCOUNT_EMAIL=jimmy@example.com
BLOG_ACCOUNT_PASSWORD=replace-with-a-secure-password
```

Copy `backend/.env.example` to a private environment file or provide these
values through the deployment environment. Ktor requires every account value
at startup. Do not commit real passwords or secrets.

## Frontend

### Session storage

The session is stored under this key:

```text
jimmy-blog.auth-session
```

Its shape is:

```ts
type AuthSession = {
  token: string;
  user: {
    id: string;
    name: string;
    email: string;
  };
};
```

Because `sessionStorage` is scoped to a browser tab, opening the blog in a new
tab does not carry over the login session. Closing the tab also clears it.

### API configuration

The frontend reads the Ktor API origin from:

```text
NEXT_PUBLIC_API_URL=http://localhost:8080
```

## API contract

### Log in

```http
POST /api/auth/login
Content-Type: application/json
```

Request:

```json
{
  "email": "jimmy@example.com",
    "password": "<configured password>"
}
```

Successful response:

```json
{
  "token": "<jwt>",
  "user": {
    "id": "usr_jimmy",
    "name": "Jimmy",
    "email": "jimmy@example.com"
  }
}
```

Invalid credentials return `401 Unauthorized`.

### Get the current user

```http
GET /api/auth/me
Authorization: Bearer <jwt>
```

A valid session returns the authenticated user profile. An invalid or expired
token returns `401 Unauthorized`.

### Log out

```http
POST /api/auth/logout
Authorization: Bearer <jwt>
```

The endpoint returns `204 No Content`. The frontend must also remove its local
session data.

## Security requirements

Before production release:

- Replace the environment-configured account with database-backed users when
  supporting multiple accounts.
- Hash passwords with Argon2id or bcrypt; never store plaintext passwords.
- Provide `JWT_SECRET` through a protected environment variable.
- Use HTTPS for all frontend and API traffic.
- Prefer short-lived access tokens with a secure, `HttpOnly`, `SameSite`
  refresh cookie instead of storing long-lived credentials in browser storage.
- Add login rate limiting and audit failed authentication attempts.
