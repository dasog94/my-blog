const LOCAL_API_URL = "http://localhost:8080";

/** @param {string} hostname */
function isLocalHost(hostname) {
  const host = hostname.toLowerCase().replace(/\.$/, "");
  return host === "localhost" || host.endsWith(".localhost") ||
    /^127\./.test(host) || /^0\./.test(host) ||
    host === "[::]" || host === "[::1]" ||
    /^\[::ffff:(?:7f[0-9a-f]{2}:[0-9a-f]{1,4}|0:0)\]$/.test(host);
}

/**
 * Resolve the public API origin shared by the build and authentication client.
 * A deployment address must be supplied by its operator, never inferred here.
 *
 * @param {string | undefined} value
 * @param {string | undefined} environment
 * @returns {string}
 */
export function resolveApiUrl(value, environment) {
  if (value === undefined && environment === "development") return LOCAL_API_URL;
  if (!value?.trim()) {
    throw new Error("NEXT_PUBLIC_API_URL is required outside local development. Set the API HTTPS origin before building the frontend.");
  }

  const input = value.trim();
  let url;
  try {
    url = new URL(input);
  } catch {
    throw new Error("NEXT_PUBLIC_API_URL must be an absolute HTTP(S) origin.");
  }

  // Check the original spelling too: URL() otherwise erases paths such as
  // /api/.. and accepts a trailing colon with no port.
  const authority = input.match(/^https?:\/\/([^/?#\\\s]+)\/?$/i)?.[1];
  if (!authority || authority.includes("@") || authority.endsWith(":") || url.port === "0" ||
      !["http:", "https:"].includes(url.protocol) || !url.hostname ||
      url.hostname.includes("*") || url.username || url.password ||
      !["", "/"].includes(url.pathname)) {
    throw new Error("NEXT_PUBLIC_API_URL must be an HTTP(S) origin without credentials, a path, query, or fragment.");
  }

  if (environment !== "development" && (url.protocol !== "https:" || isLocalHost(url.hostname))) {
    throw new Error("NEXT_PUBLIC_API_URL must use HTTPS and a non-local host outside local development.");
  }

  return url.origin;
}
