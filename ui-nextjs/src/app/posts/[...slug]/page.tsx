import { notFound } from "next/navigation";
import { ApiError, getCommentsBySlug, getPostBySlug } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { CommentNode, CommentThreadResponse, ContentDetail } from "@/lib/types";
import { CommentCreateForm } from "@/components/comment-create-form";

function CommentTree({ node }: { node: CommentNode }) {
  return (
    <article className="comment">
      <p className="meta">
        {node.authorDisplayName} at {formatDate(node.createdAt)}
      </p>
      {node.deleted ? (
        <p>[deleted]</p>
      ) : (
        <div className="post-body" dangerouslySetInnerHTML={{ __html: node.renderedHtml }} />
      )}
      {node.children.length > 0 ? (
        <div className="comment-children">
          {node.children.map((child) => (
            <CommentTree key={child.id} node={child} />
          ))}
        </div>
      ) : null}
    </article>
  );
}

export default async function PostPage({
  params,
}: {
  params: Promise<{ slug: string[] }>;
}) {
  const resolved = await params;
  const slugPath = resolved.slug.join("/");
  const [year = "", month = "", ...slugParts] = resolved.slug;
  const shortSlug = slugParts.join("/");

  let post: ContentDetail;
  try {
    post = await getPostBySlug(slugPath);
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
  try {
    thread = await getCommentsBySlug(slugPath);
  } catch {
    // Show post even if comments endpoint is currently unavailable.
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

      <section className="post-body" dangerouslySetInnerHTML={{ __html: post.renderedHtml }} />

      <section className="comment-block">
        <h2 className="comment-title">Comments ({thread?.totalActiveCount ?? 0})</h2>
        {year && month && shortSlug ? (
          <CommentCreateForm year={year} month={month} slug={shortSlug} />
        ) : null}
        {thread === null ? (
          <p>Comments are temporarily unavailable.</p>
        ) : thread.comments.length === 0 ? (
          <p>No comments yet.</p>
        ) : (
          thread.comments.map((comment) => <CommentTree key={comment.id} node={comment} />)
        )}
      </section>
    </article>
  );
}
