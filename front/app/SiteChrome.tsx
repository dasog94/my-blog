import { AuthNav } from "./AuthNav";
import Link from "next/link";

export function SiteHeader() {
  return <><a className="skip-link" href="#main">Skip to content</a><header className="site-header shell" id="top"><Link className="wordmark" href="/" aria-label="Jimmy’s Notes home"><span className="mark" aria-hidden="true">j.</span><span>Jimmy’s Notes<span className="brand-dot" aria-hidden="true">✳</span></span></Link><nav aria-label="Primary navigation"><Link href="/#writing">The notebook</Link><Link href="/#about">About</Link><span className="nav-divider" /><AuthNav /></nav></header></>;
}

export function SiteFooter() {
  return <footer className="site-footer shell"><Link className="wordmark" href="/">Jimmy’s Notes<span className="footer-dot">.</span></Link><p>Written with curiosity. Built with care.</p><div><span>© {new Date().getFullYear()} Jimmy</span><a href="https://github.com/dasog94/my-blog" target="_blank" rel="noreferrer">Source <span aria-hidden="true">↗</span></a></div></footer>;
}
