"use client";

import { FormEvent, useEffect, useState } from "react";
import { login, readSession, saveSession } from "../auth";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (readSession()) window.location.replace("/");
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError("");
    try {
      const session = await login(email.trim(), password);
      saveSession(session);
      window.location.assign("/");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Unable to sign in.");
      setLoading(false);
    }
  }

  return (
    <main className="login-page">
      <a className="wordmark login-brand" href="/">
        <span className="mark">J</span><span>Jimmy’s Notes</span>
      </a>
      <section className="login-panel" aria-labelledby="login-title">
        <div className="section-label">Welcome back</div>
        <h1 id="login-title">Log in to your journal.</h1>
        <p className="login-intro">Manage drafts, bookmarks, and your reading list.</p>
        <form onSubmit={submit}>
          <label htmlFor="email">Email address</label>
          <input id="email" type="email" autoComplete="email" required value={email} onChange={(event) => setEmail(event.target.value)} />
          <label htmlFor="password">Password</label>
          <input id="password" type="password" autoComplete="current-password" required minLength={8} value={password} onChange={(event) => setPassword(event.target.value)} />
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="login-button" type="submit" disabled={loading}>{loading ? "Signing in…" : "Log in"}<span>→</span></button>
        </form>
      </section>
      <div className="login-quote">“Writing is thinking made visible.”</div>
    </main>
  );
}
