import type { Metadata } from "next";
import Link from "next/link";
import { SiteHeader, SiteFooter } from "./SiteChrome";
import { Notebook } from "./Notebook";
import { SystemSketch } from "./SystemSketch";

export const metadata: Metadata = {
  title: "Jimmy’s Notes — A little clarity in a complicated world",
  description: "Notes on software, systems, and the craft of making things. A personal journal by Jimmy.",
};

export default function Home() {
  return <>
    <SiteHeader />
    <main id="main" className="shell">
      <section className="hero" aria-labelledby="hero-title">
        <div className="hero-copy">
          <div className="eyebrow"><span className="status-dot" /> A DEVELOPER’S FIELD NOTES</div>
          <h1 id="hero-title">Making things.<br />Making <em>sense.</em></h1>
          <p>I’m Jimmy. I build software and write to understand it.<br className="desktop-break" /> Notes on systems, small discoveries, and the work in between.</p>
          <a className="text-link" href="#writing">Explore the notebook <span aria-hidden="true">↓</span></a>
        </div>
        <div className="hero-drawing"><SystemSketch /><span className="drawing-caption">FIG. 01 — THINK. BUILD. SIMPLIFY. REPEAT.</span></div>
      </section>
      <div className="journal-layout">
        <div className="journal-main">
          <section className="featured" aria-labelledby="feature-title">
            <div className="section-kicker"><span><span className="tiny-star" aria-hidden="true">✳</span> THE FEATURED NOTE</span><span>NO. 004</span></div>
            <Link className="feature-card" href="/writing/the-quiet-craft">
              <div className="feature-copy">
                <div className="meta"><span className="category">Engineering</span><span>August 12, 2026</span></div>
                <h2 id="feature-title">The quiet craft of<br />making things simple</h2>
                <p>Simplicity isn’t where a project begins. It’s what remains after the hard decisions and careful edits.</p>
                <div className="feature-bottom"><span className="read-link">Read the note <span aria-hidden="true">↗</span></span><span>4 min read</span></div>
              </div>
              <div className="feature-visual" aria-hidden="true"><div className="wire-cube cube-one" /><div className="wire-cube cube-two" /><div className="wire-cube cube-three" /><span className="visual-label">LESS, BUT BETTER.</span></div>
            </Link>
          </section>
          <Notebook />
        </div>
        <aside className="journal-sidebar">
          <section className="about-card" id="about" aria-labelledby="about-title">
            <div className="author-avatar" aria-hidden="true">j<span>✳</span></div>
            <div className="eyebrow">THE PERSON BEHIND THE NOTES</div>
            <h2 id="about-title">Hi, I’m Jimmy<span>.</span></h2>
            <p>A developer drawn to clear ideas, useful software, and the occasional rabbit hole.</p>
            <p>This is my open notebook. A place to think out loud and leave a few things better explained.</p>
            <a className="text-link" href="https://github.com/dasog94" target="_blank" rel="noreferrer">Find me on GitHub <span aria-hidden="true">↗</span></a>
          </section>
          <section className="currently"><div className="section-kicker"><span>ON MY WORKBENCH</span><span className="status-dot" /></div><h3>This little corner of the web</h3><p>Building a home for my notes, one thoughtful detail at a time.</p><div className="stack-tags"><span>Next.js</span><span>Kotlin</span><span>Ktor</span></div><a className="text-link" href="https://github.com/dasog94/my-blog" target="_blank" rel="noreferrer">Follow the build <span aria-hidden="true">↗</span></a></section>
          <div className="margin-note"><span aria-hidden="true">“</span><p>The best way to understand something is to try to explain it.</p><div>A REMINDER TO MYSELF</div></div>
        </aside>
      </div>
      <section className="closing-note"><span aria-hidden="true">✳</span><p>A small corner of the internet.<br /><em>Room for a little more thought.</em></p><a href="#top" aria-label="Back to top">↑</a></section>
    </main>
    <SiteFooter />
  </>;
}
