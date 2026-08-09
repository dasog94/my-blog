"use client";

import { useEffect, useState } from "react";
import { clearSession, readSession, type AuthUser } from "./auth";

export function AuthNav() {
  const [user, setUser] = useState<AuthUser | null>(null);

  useEffect(() => {
    const sync = () => setUser(readSession()?.user ?? null);
    sync();
    window.addEventListener("auth-session-change", sync);
    return () => window.removeEventListener("auth-session-change", sync);
  }, []);

  if (!user) return <a className="login-link" href="/login">Log in</a>;

  return (
    <div className="account">
      <span className="avatar" aria-hidden="true">{user.name.charAt(0).toUpperCase()}</span>
      <span>{user.name}</span>
      <button type="button" onClick={clearSession}>Log out</button>
    </div>
  );
}
