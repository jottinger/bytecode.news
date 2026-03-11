import Link from "next/link";
import { PostSummary } from "@/lib/api/types";
import { cn } from "@/lib/utils";

interface PostCardProps {
  post: PostSummary;
  variant?: "default" | "featured" | "lead" | "secondary" | "brief";
}

function formatPostDate(dateStr: string): string {
  const date = new Date(dateStr);
  return date.toLocaleDateString("en-US", {
    month: "long",
    day: "numeric",
    year: "numeric",
  });
}

function buildPostHref(post: PostSummary): string {
  const date = new Date(post.publishedAt);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  return `/posts/${year}/${month}/${post.slug}`;
}

export function PostCard({ post, variant = "default" }: PostCardProps) {
  const href = buildPostHref(post);
  const resolvedVariant = variant === "featured" ? "lead" : variant;

  if (resolvedVariant === "lead") {
    return <LeadCard post={post} href={href} />;
  }

  if (resolvedVariant === "secondary") {
    return <SecondaryCard post={post} href={href} />;
  }

  if (resolvedVariant === "brief") {
    return <BriefCard post={post} href={href} />;
  }

  return <DefaultCard post={post} href={href} />;
}

function LeadCard({ post, href }: { post: PostSummary; href: string }) {
  return (
    <Link href={href} className="group block">
      <article>
        <div className="byline text-muted-foreground mb-4 flex items-center gap-2">
          <span>By {post.authorDisplayName}</span>
          <span className="text-border">|</span>
          <time dateTime={post.publishedAt}>{formatPostDate(post.publishedAt)}</time>
        </div>

        <h2 className="headline-lead text-foreground group-hover:text-amber transition-colors duration-200">
          {post.title}
        </h2>

        {post.excerpt && (
          <p className="article-excerpt mt-4 max-w-2xl">
            {post.excerpt}
          </p>
        )}

        <div className="mt-5 flex items-center gap-2">
          <div className="h-px w-6 bg-amber/40 transition-all duration-300 group-hover:w-10 group-hover:bg-amber" />
          <span className="section-label text-amber-dim group-hover:text-amber transition-colors">
            Read article
          </span>
        </div>
      </article>
    </Link>
  );
}

function SecondaryCard({ post, href }: { post: PostSummary; href: string }) {
  return (
    <Link href={href} className="group block">
      <article>
        <div className="byline text-muted-foreground/70 mb-2.5 flex items-center gap-2">
          <time dateTime={post.publishedAt}>{formatPostDate(post.publishedAt)}</time>
        </div>

        <h3 className="headline-secondary text-foreground group-hover:text-amber transition-colors duration-200">
          {post.title}
        </h3>

        {post.excerpt && (
          <p className="text-muted-foreground text-sm leading-relaxed mt-2.5 line-clamp-3">
            {post.excerpt}
          </p>
        )}

        <p className="byline text-muted-foreground/50 mt-3">
          By {post.authorDisplayName}
        </p>
      </article>
    </Link>
  );
}

function BriefCard({ post, href }: { post: PostSummary; href: string }) {
  return (
    <Link href={href} className="group block">
      <article>
        <h4 className="headline-brief text-foreground group-hover:text-amber transition-colors duration-200">
          {post.title}
        </h4>

        {post.excerpt && (
          <p className="text-muted-foreground/70 text-sm leading-relaxed mt-2 line-clamp-2">
            {post.excerpt}
          </p>
        )}

        <div className="byline text-muted-foreground/50 mt-2.5 flex items-center gap-2">
          <span>{post.authorDisplayName}</span>
          <span className="text-border/40">|</span>
          <time dateTime={post.publishedAt}>{formatPostDate(post.publishedAt)}</time>
        </div>
      </article>
    </Link>
  );
}

function DefaultCard({ post, href }: { post: PostSummary; href: string }) {
  return (
    <Link href={href} className="group block">
      <article
        className={cn(
          "border-border/40 relative border-b py-5 transition-all duration-200",
          "hover:border-amber/30"
        )}
      >
        <div className="bg-amber absolute left-0 top-0 bottom-0 w-px opacity-0 transition-opacity duration-300 group-hover:opacity-100" />

        <div className="byline text-muted-foreground mb-2.5 flex items-center gap-2 pl-0 group-hover:pl-4 transition-all duration-200">
          <time dateTime={post.publishedAt}>{formatPostDate(post.publishedAt)}</time>
          <span className="text-border/40">|</span>
          <span>{post.authorDisplayName}</span>
        </div>

        <h3 className="headline-secondary text-foreground group-hover:text-amber transition-all duration-200 pl-0 group-hover:pl-4">
          {post.title}
        </h3>

        {post.excerpt && (
          <p className="text-muted-foreground text-sm leading-relaxed mt-2 line-clamp-2 pl-0 group-hover:pl-4 transition-all duration-200">
            {post.excerpt}
          </p>
        )}
      </article>
    </Link>
  );
}
