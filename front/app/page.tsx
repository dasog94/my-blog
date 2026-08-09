import type { Metadata } from "next";
import { AuthNav } from "./AuthNav";

export const metadata: Metadata = {
  title: "Jimmy’s Notes — Software, systems & the work between",
  description:
    "Field notes on building software, learning in public, and making thoughtful things.",
};

const posts = [
  {
    date: "August 7, 2026",
    title: "Building a writing habit that survives busy weeks",
    excerpt:
      "A small system for capturing ideas, shaping drafts, and publishing without waiting for perfect conditions.",
    tags: ["Process", "Writing"],
    time: "6 min read",
  },
  {
    date: "July 29, 2026",
    title: "Why I reached for Ktor on my next side project",
    excerpt:
      "What I wanted from a small backend, where Kotlin shines, and the trade-offs I’m willing to make.",
    tags: ["Kotlin", "Backend"],
    time: "8 min read",
  },
  {
    date: "July 18, 2026",
    title: "The boring architecture is usually the good one",
    excerpt:
      "A case for clear boundaries, fewer abstractions, and systems that remain easy to explain six months later.",
    tags: ["Architecture"],
    time: "5 min read",
  },
];

function Arrow() {
  return <span aria-hidden="true">↗</span>;
}

export default function Home() {
  return (
    <main>
      <header className="site-header shell">
        <a className="wordmark" href="#top" aria-label="Jimmy’s Notes home">
          <span className="mark">J</span>
          <span>Jimmy’s Notes</span>
        </a>
        <nav aria-label="Primary navigation">
          <a href="#writing">Writing</a>
          <a href="#about">About</a>
          <AuthNav />
          <button className="search" type="button" aria-label="Search articles">
            <span aria-hidden="true">⌕</span> Search
          </button>
        </nav>
      </header>

      <section className="hero shell" id="top">
        <div className="eyebrow"><span /> A personal field journal</div>
        <h1>Software, systems,<br />and the <em>work between.</em></h1>
        <p className="intro">
          I’m Jimmy, a developer who writes about building useful things,
          learning in public, and finding clarity in complicated systems.
        </p>
        <a className="primary-link" href="#writing">Read the latest <span>↓</span></a>
        <aside className="hero-note">
          <span className="note-no">NOTE 001</span>
          <p>Good software starts with a clear explanation.</p>
        </aside>
      </section>

      <section className="featured shell" id="writing">
        <div className="section-label">Featured essay</div>
        <article className="feature-card">
          <div className="feature-art" aria-hidden="true">
            <span className="code-fragment one">{`fun idea()`}</span>
            <span className="code-fragment two">{`{ ship() }`}</span>
            <span className="orb" />
            <span className="grid-shape" />
          </div>
          <div className="feature-copy">
            <div className="meta"><span>Engineering</span><span>10 min read</span></div>
            <h2>The quiet craft of making things simple</h2>
            <p>
              Simplicity isn’t where a project begins. It’s what remains after
              the hard decisions, careful edits, and honest conversations.
            </p>
            <a href="#article">Read essay <Arrow /></a>
          </div>
        </article>
      </section>

      <section className="latest shell">
        <div className="section-heading">
          <div>
            <div className="section-label">From the notebook</div>
            <h2>Latest writing</h2>
          </div>
          <a href="#archive">View all articles <span>→</span></a>
        </div>
        <div className="post-list">
          {posts.map((post, index) => (
            <article className="post" key={post.title}>
              <div className="post-number">0{index + 1}</div>
              <div className="post-copy">
                <time>{post.date}</time>
                <h3><a href="#article">{post.title}</a></h3>
                <p>{post.excerpt}</p>
                <div className="tags">
                  {post.tags.map((tag) => <span key={tag}>{tag}</span>)}
                </div>
              </div>
              <div className="read-time">{post.time}<Arrow /></div>
            </article>
          ))}
        </div>
      </section>

      <section className="about shell" id="about">
        <div className="section-label">A little context</div>
        <div className="about-grid">
          <h2>I make software.<br />I write to understand it.</h2>
          <div>
            <p>
              This is my corner of the internet for notes on Kotlin, product
              engineering, and the practical details that turn an idea into
              something real.
            </p>
            <a href="#more">More about me <Arrow /></a>
          </div>
        </div>
      </section>

      <footer className="shell">
        <div className="wordmark"><span className="mark">J</span><span>Jimmy’s Notes</span></div>
        <p>Thoughtfully made with Next.js + Ktor.</p>
        <div><a href="#github">GitHub</a><a href="#rss">RSS</a></div>
      </footer>
    </main>
  );
}
