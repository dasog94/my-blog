import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { posts } from "../../posts";
import { SiteHeader, SiteFooter } from "../../SiteChrome";

type Props = { params: Promise<{ slug: string }> };
export function generateStaticParams() { return posts.map(post => ({ slug: post.slug })); }
export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const post = posts.find(item => item.slug === slug);
  return { title: post ? `${post.title} — Jimmy’s Notes` : "Note not found — Jimmy’s Notes", description: post?.excerpt };
}
export default async function Article({ params }: Props) {
  const { slug } = await params;
  const post = posts.find(item => item.slug === slug);
  if (!post) notFound();
  const next = posts[(posts.indexOf(post) + 1) % posts.length];
  return <><SiteHeader /><main id="main" className="article-shell"><Link className="article-back text-link" href="/#writing"><span aria-hidden="true">←</span> Back to the notebook</Link><article><header className="article-heading"><div className="meta"><span className="category">{post.category}</span><time dateTime={post.isoDate}>{post.date}</time><span>{post.readTime} read</span></div><h1>{post.title}</h1><p className="article-deck">{post.excerpt}</p><div className="article-byline"><span className="mini-avatar" aria-hidden="true">j.</span> By Jimmy <span>·</span> Field notes on making things</div></header><div className="article-body">{post.sections.map(section => <section key={section.title}><h2>{section.title}</h2>{section.paragraphs.map(paragraph => <p key={paragraph}>{paragraph}</p>)}</section>)}</div><div className="article-signoff"><span aria-hidden="true">✳</span><p>Thanks for reading.<br /><span>— Jimmy</span></p></div></article><Link className="next-note" href={`/writing/${next.slug}`}><span className="eyebrow">KEEP EXPLORING</span><h2>{next.title} <span aria-hidden="true">↗</span></h2></Link></main><SiteFooter /></>;
}
