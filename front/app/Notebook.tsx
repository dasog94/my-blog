"use client";
import { useState } from "react";
import { posts } from "./posts";

const topics = ["All notes", "Engineering", "Kotlin", "Process"];
export function Notebook() {
  const [topic, setTopic] = useState("All notes");
  const [query, setQuery] = useState("");
  const visible = posts.filter(post => (topic === "All notes" || post.category === topic) && `${post.title} ${post.excerpt} ${post.category}`.toLowerCase().includes(query.trim().toLowerCase()));
  return <section className="notebook" id="writing" aria-labelledby="notebook-title">
    <div className="notebook-heading"><h2 id="notebook-title">The notebook<span> / 04</span></h2><span className="small-label">IDEAS, WORKING OUT LOUD.</span></div>
    <div className="notebook-tools"><div className="topic-filters" aria-label="Filter notes by topic">{topics.map(item => <button key={item} type="button" aria-pressed={topic === item} onClick={() => setTopic(item)}>{item}</button>)}</div><label className="search-field"><svg viewBox="0 0 20 20" width="17" height="17" fill="none" aria-hidden="true"><circle cx="8" cy="8" r="5.5" stroke="currentColor" strokeWidth="1.5" /><path d="m12 12 5 5" stroke="currentColor" strokeWidth="1.5" /></svg><input type="search" aria-label="Search notes" placeholder="Find a note…" value={query} onChange={event => setQuery(event.target.value)} /></label></div>
    <p className="sr-only" role="status">{visible.length} {visible.length === 1 ? "note" : "notes"} found</p>
    <div className="post-list">{visible.map(post => <article className="post" key={post.slug}><div className="post-date"><time dateTime={post.isoDate}>{post.date.replace(", 2026", "")}</time><span>2026</span></div><div className="post-copy"><div className="post-meta"><span>{post.category}</span><span>{post.readTime} read</span></div><h3><a href={`/writing/${post.slug}`}>{post.title}<span className="post-arrow" aria-hidden="true">↗</span></a></h3><p>{post.excerpt}</p></div></article>)}</div>
    {visible.length === 0 && <div className="empty-state"><h3>No notes found.</h3><p>Try another phrase or explore all topics.</p><button type="button" onClick={() => { setTopic("All notes"); setQuery(""); }}>Clear filters <span aria-hidden="true">↗</span></button></div>}
    <div className="notebook-end"><span>{query || topic !== "All notes" ? `${visible.length} of ${posts.length} notes` : "You’re all caught up."}</span><span>More thoughts are taking shape <span aria-hidden="true">✳</span></span></div>
  </section>;
}
