export type AuthUser = {
  id: string;
  name: string;
  email: string;
};

export type AuthSession = {
  token: string;
  user: AuthUser;
};

const SESSION_KEY = "jimmy-blog.auth-session";
const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export function readSession(): AuthSession | null {
  if (typeof window === "undefined") return null;
  const value = window.sessionStorage.getItem(SESSION_KEY);
  if (!value) return null;
  try {
    return JSON.parse(value) as AuthSession;
  } catch {
    window.sessionStorage.removeItem(SESSION_KEY);
    return null;
  }
}

export function saveSession(session: AuthSession) {
  window.sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
  window.dispatchEvent(new Event("auth-session-change"));
}

export function clearSession() {
  window.sessionStorage.removeItem(SESSION_KEY);
  window.dispatchEvent(new Event("auth-session-change"));
}

export async function login(email: string, password: string): Promise<AuthSession> {
  const response = await fetch(`${API_URL}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => null) as { message?: string } | null;
    throw new Error(error?.message ?? "Unable to sign in.");
  }
  return response.json() as Promise<AuthSession>;
}

export async function getCurrentUser(token: string): Promise<AuthUser> {
  const response = await fetch(`${API_URL}/api/auth/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) throw new Error("Your session has expired.");
  return response.json() as Promise<AuthUser>;
}
