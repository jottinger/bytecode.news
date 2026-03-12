import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { cache } from "react";
import { ApiError, getCommentsBySlug, getPageBySlug, getPostBySlug } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { CommentThreadResponse, ContentDetail } from "@/lib/types";
import { CommentCreateForm } from "@/components/comment-create-form";
import { CommentThread } from "@/components/comment-thread";
import { HighlightedHtml } from "@/components/highlighted-html";
import { PostActions } from "@/components/post-actions";

const SUMMARY_FALLBACK = "Read the latest content on bytecode.news.";

const getResolvedPost = cache(async (slugPath: string, isDatedPostPath: boolean) => {
  return isDatedPostPath ? getPostBySlug(slugPath) : getPageBySlug(slugPath);
});

function normalizePath(slug: string[]) {
  const slugPath = slug.join("/");
  const [year = "", month = "", ...slugParts] = slug;
  const shortSlug = slugParts.join("/");
  const isDatedPostPath = Boolean(year && month && shortSlug);
  return { slugPath, year, month, shortSlug, isDatedPostPath };
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string[] }>;
}): Promise<Metadata> {
  const resolved = await params;
  const { slugPath, isDatedPostPath } = normalizePath(resolved.slug);

  try {
    const post = await getResolvedPost(slugPath, isDatedPostPath);
    const description = post.excerpt?.trim() || SUMMARY_FALLBACK;

    return {
      title: post.title,
      description,
      openGraph: {
        title: post.title,
        description,
        type: "article",
      },
      twitter: {
        card: "summary",
        title: post.title,
        description,
      },
    };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return {
        title: "Post Not Found",
        description: "The requested post could not be found.",
      };
    }

    return {
      title: "Post Unavailable",
      description: "The requested post is temporarily unavailable.",
    };
  }
}

export default async function PostPage({
  params,
}: {
  params: Promise<{ slug: string[] }>;
}) {
  const resolved = await params;
  const { slugPath, year, month, shortSlug, isDatedPostPath } = normalizePath(resolved.slug);

  let post: ContentDetail;
  try {
    post = await getResolvedPost(slugPath, isDatedPostPath);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound();
    }
    return (
      <section className="notice">
        <h2>Post Unavailable</h2>
        <p>The backend is temporarily unavailable. Please refresh shortly.</p>
      </section>
    );
  }

  let thread: CommentThreadResponse | null = null;
  const commentsAllowed = !post.categories.includes("_sidebar");
  if (commentsAllowed) {
    try {
      thread = await getCommentsBySlug(slugPath);
    } catch {
      // Show post even if comments endpoint is currently unavailable.
    }
  }

  return (
    <article className="post">
      <header>
        <h1>{post.title}</h1>
        <p className="meta">
          {formatDate(post.publishedAt)} | {post.authorDisplayName}
        </p>
      </header>

      {post.tags.length > 0 ? (
        <div className="tags">
          {post.tags.map((tag) => (
            <span className="tag" key={tag}>
              {tag}
            </span>
          ))}
        </div>
      ) : null}

      <HighlightedHtml className="post-body" html={post.renderedHtml} />

      <PostActions postId={post.id} />
      {commentsAllowed ? (
        <section className="comment-block">
          <h2 className="comment-title">Comments ({thread?.totalActiveCount ?? 0})</h2>
          {isDatedPostPath ? <CommentCreateForm year={year} month={month} slug={shortSlug} /> : null}
          {thread === null ? (
            <p>Comments are temporarily unavailable.</p>
          ) : thread.comments.length === 0 ? (
            <p>No comments yet.</p>
          ) : (
            <CommentThread comments={thread.comments} />
          )}
        </section>
      ) : null}
    </article>
  );
}
