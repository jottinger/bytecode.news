import { notFound } from "next/navigation";
import { ApiError, getCommentsBySlug, getPageBySlug, getPostBySlug } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { CommentThreadResponse, ContentDetail } from "@/lib/types";
import { CommentCreateForm } from "@/components/comment-create-form";
import { CommentThread } from "@/components/comment-thread";
import { HighlightedHtml } from "@/components/highlighted-html";
import { PostActions } from "@/components/post-actions";

export default async function PostPage({
  params,
}: {
  params: Promise<{ slug: string[] }>;
}) {
  const resolved = await params;
  const slugPath = resolved.slug.join("/");
  const [year = "", month = "", ...slugParts] = resolved.slug;
  const shortSlug = slugParts.join("/");
  const isDatedPostPath = Boolean(year && month && shortSlug);

  let post: ContentDetail;
  try {
    post = isDatedPostPath ? await getPostBySlug(slugPath) : await getPageBySlug(slugPath);
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
