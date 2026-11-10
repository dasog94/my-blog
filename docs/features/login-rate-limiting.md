# Login attempt throttling

`POST /api/auth/login` has two application-local rolling request budgets. These
apply before parsing the body or comparing credentials. All admitted attempts
consume capacity, including successful logins, incorrect passwords, unknown
emails, and malformed JSON. Changing the email does not select a new bucket.
Successful authentication does not clear counters.

| Environment variable | Default | Accepted values |
| --- | --- | --- |
| `LOGIN_RATE_LIMIT_CLIENT_ATTEMPTS` | `5` | `1` through the total-attempt limit |
| `LOGIN_RATE_LIMIT_TOTAL_ATTEMPTS` | `100` | `1` through `10000` |
| `LOGIN_RATE_LIMIT_WINDOW_SECONDS` | `60` | `1` through `3600` |
| `LOGIN_TRUSTED_PROXY_CIDRS` | omitted (trust no proxies) | Comma-separated numeric IPv4/IPv6 CIDRs; `/0` is rejected |

Settings are read and validated once at startup. Blank, malformed, zero,
negative, or out-of-range settings fail startup rather than disable protection.
There is no environment switch to disable the limits.

`DeploymentConfig.fromEnvironment(env)` includes this policy alongside the
account and validated CORS origins. `Application.module()` uses that same
configuration path, so enabling throttling does not bypass production startup
validation. See the [deployment guide](../render.md) for the complete contract.

## HTTP and recovery policy

Within the budgets, invalid credentials still return the generic `401`
response and valid credentials still issue the existing JWT. An exhausted
budget returns:

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: 60
Cache-Control: no-store

{"message":"Too many login attempts. Try again later."}
```

`Retry-After` is the positive number of seconds, rounded up, until both budgets
would permit another request, assuming no other intervening admissions. Each
slot expires one window after its own admission; this is a rolling window, not
a reset at wall-clock minute boundaries. Blocked retries neither consume slots
nor extend the wait. Even correct credentials receive `429` during cooldown.
The server uses monotonic time, so wall-clock corrections cannot reset limits.

Allowed CORS origins receive the usual CORS headers on `429`, and `Retry-After`
is exposed to browser JavaScript. `/health`, authenticated `/api/auth/me`,
`/api/auth/logout`, and CORS preflights do not consume login capacity. Existing
JWT sessions keep working while new logins are throttled.

## Client identity and proxy trust

The default key is the numeric transport peer from
`call.request.local.remoteAddress`, not the mutable `request.origin`. All
forwarding headers are ignored by default. Equivalent IPv6 literals and
IPv4-mapped IPv6 addresses are canonicalized. No header/configuration hostname
is resolved through DNS.

If, and only if, the socket peer belongs to `LOGIN_TRUSTED_PROXY_CIDRS`, the
resolver reads **all** `X-Forwarded-For` header lines as a single chain and walks
from right to left. It skips only explicitly trusted proxy addresses and uses
the first untrusted hop as the client boundary. Attacker-supplied entries to
the left of that boundary cannot change the key. Arbitrary private addresses
are not implicitly trusted. `Forwarded`, `X-Real-IP`, `CF-Connecting-IP`, and
`True-Client-IP` are not used.

For a trusted transport peer, missing, malformed, excessive (over 4096
characters or 32 hops), or entirely trusted chains return a generic `400`
before credentials are read. They cannot supply a fallback bucket to bypass an
exhausted client limit. An invalid transport peer uses one fixed
`unresolved-peer` bucket. Neither case creates keys from arbitrary strings.
Malformed entries to the left of an already identified client are irrelevant.

Only add CIDRs for proxies you control or whose behavior and address ranges
are verified. Every trusted hop must append or replace the source address
correctly. Network controls must prevent bypassing that ingress path. Do not
trust an entire private network just because the load balancer has a private
address. IP address sharing (NAT) can make unrelated users share a budget;
rotating addresses, including IPv6 addresses, is bounded by the aggregate cap.

## Render deployment decision

The checked-in Blueprint uses Render's Free web service plan and one
application process. Render documents Cloudflare/load-balancer forwarding and
recommends application-level limits, but this repository does not establish the
actual service's proxy CIDRs or a fixed number of hops. Render's public header
discussion also contains conflicting historical reports about overwriting
versus appending incoming `X-Forwarded-For` values.

Consequently the Blueprint deliberately leaves `LOGIN_TRUSTED_PROXY_CIDRS`
unset. It does not select the leftmost forwarded value, guess a hop count, or
automatically trust Cloudflare-specific headers when `RENDER=true`.
Behind Render, requests may share a transport-proxy bucket and the five-attempt
limit; this conservative availability tradeoff is intentional until proxy
identity is verified. The separate 100-attempt process budget bounds guessing
even when requests arrive through different proxies or attacker-controlled IPs.
It can also temporarily prevent all new logins during an attack. This is a
small single-account blog policy, not an account-wide permanent lockout.

To enable distinct client budgets behind a verified ingress:

1. Confirm the actual immediate-peer and upstream-proxy ranges for the service
   with the provider. Check both the `onrender.com` address and custom domains.
2. Verify normal, repeated-header, and forged-prefix requests from two clients.
   Confirm which hop overwrites or appends the true source address and that
   alternate/direct ingress cannot bypass it. Avoid logging request bodies,
   passwords, tokens, or unfiltered headers during verification.
3. Set only those verified CIDRs in `LOGIN_TRUSTED_PROXY_CIDRS` and redeploy.
   Confirm five attempts from one client do not exhaust another client's budget,
   forged prefixes do not bypass a block, and aggregate exhaustion still works.
   Recheck this when proxy topology or provider behavior changes.

No live Render service or upstream protections were verified by this change.

## Memory, restarts, scaling, and auditing

Admissions and reservations for both budgets occur under one short JVM lock,
so parallel requests cannot overshoot either budget. No lock is held while
reading a body, checking credentials, issuing a JWT, or sending a response.
Only admitted requests create keys; the aggregate cap bounds the number of
stored clients and timestamps. Expired entries are removed on the next login
attempt. Idle state remains bounded, and rejected attempts do not allocate
new client buckets.

This state exists only in the running JVM. It is lost on a Render sleep,
restart, crash, or redeploy, and each process/instance has an independent
budget. Render's Free plan cannot scale beyond one instance and may restart
at any time; deployment overlap can still have independent budgets. This is
useful application-side protection, not a durable or cluster-wide guarantee.
Do not add worker processes or scale out relying on these counters. For a
durable multi-instance limit, use an atomic shared store such as Redis/Render
Key Value with expiry for both budgets (and an explicit fail-closed outage
policy), or a verified ingress limiter. Do not replace the store with a local
file on Render's ephemeral filesystem.

Each admitted credential mismatch emits `auth.login.failed client=<key>` through
the application logger. Only canonical addresses or fixed fallback keys are
included: never submitted emails, passwords, JWTs, bodies, or raw headers.
These failure events are themselves bounded by the admission budgets. Throttled
and malformed requests are not credential-failure events and are not logged per
request, avoiding unlimited log amplification. Configure appropriate access
control, retention, and durable collection of authentication logs before
production; an in-process log statement is not an audit datastore.

## Verification

With Java 21 and Gradle 8.14.3, run from `backend/`:

```sh
gradle --no-daemon test installDist
```

Tests use an injected monotonic clock (no waiting for real cooldowns), plus a
real Netty socket for transport/proxy integration, and cover
the HTTP behavior, legitimate sessions, exact and rolling recovery, malformed
bodies, concurrency, rotating usernames/addresses, proxy spoofing, repeated
headers, IPv6 normalization, and invalid configuration.

References:

- [Ktor rate limiting](https://ktor.io/docs/server-rate-limit.html): the built-in
  token bucket is appropriate for basic request limits. This focused rolling
  limiter reserves both budgets atomically and bounds per-client state with the
  aggregate cap.
- [Ktor forwarded headers](https://ktor.io/docs/server-forward-headers.html):
  `XForwardedHeaders` defaults to selecting the first forwarded entry; it is not
  installed for authentication identity here.
- [Render DDoS and application-level limits](https://render.com/articles/how-render-handles-ddos-attacks).
- [Render forwarded-header discussion](https://feedback.render.com/features/p/send-the-correct-x-forwarded-for).
- [Render Free service limitations](https://render.com/docs/free).
