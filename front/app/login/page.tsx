"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { login, readSession, saveSession } from "../auth";
import { SiteHeader } from "../SiteChrome";
import { SystemSketch } from "../SystemSketch";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

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
    <div className="login-page">
      <SiteHeader />
      <main id="main" className="login-layout">
      <aside className="login-aside"><div className="eyebrow">A LITTLE SPACE TO THINK</div><h2>Good ideas<br />start with<br /><em>a small note.</em></h2><p>A personal notebook for the things we build and the things we learn along the way.</p><SystemSketch /></aside>
      <section className="login-panel" aria-labelledby="login-title">
        <div className="eyebrow">JIMMY’S NOTES / ACCOUNT</div>
        <h1 id="login-title">Welcome back.</h1>
        <p className="login-intro">Sign in to your account. Just here to read? The notebook is always open.</p>
        <form onSubmit={submit}>
          <label htmlFor="email">Email address</label>
          <input id="email" type="email" autoComplete="email" required value={email} onChange={(event) => setEmail(event.target.value)} />
          <label htmlFor="password">Password</label>
          <div className="password-field"><input id="password" type={showPassword ? "text" : "password"} autoComplete="current-password" required minLength={8} value={password} onChange={(event) => setPassword(event.target.value)} /><button type="button" aria-label={showPassword ? "Hide password" : "Show password"} aria-pressed={showPassword} onClick={() => setShowPassword(!showPassword)}>{showPassword ? "Hide" : "Show"}</button></div>
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="login-button" type="submit" disabled={loading}>{loading ? "Signing in…" : "Log in"}<span>→</span></button>
        </form>
        <Link className="login-return" href="/">← Back to the notebook</Link>
      </section>
      </main>
    </div>
  );
}
