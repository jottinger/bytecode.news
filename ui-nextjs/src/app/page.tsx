import Link from "next/link";
import { listPosts } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { ContentListResponse } from "@/lib/types";

export default async function Home({
  searchParams,
}: {
  searchParams?: Promise<{ page?: string }>;
}) {
  const params = (await searchParams) || {};
  const page = Number.parseInt(params.page || "0", 10);
  const safePage = Number.isFinite(page) && page >= 0 ? page : 0;

  let list: ContentListResponse | null = null;
  try {
    list = await listPosts(safePage, 20);
  } catch {
    return (
      <section className="notice">
        <h2>Backend Temporarily Unavailable</h2>
        <p>The blog API is currently unreachable. Please refresh in a moment.</p>
      </section>
    );
  }

  return (
    <>
      {list.posts.length === 0 ? (
        <section className="notice">
          <p>No posts yet.</p>
        </section>
      ) : (
        <section className="story-list">
          {list.posts.map((post) => (
            <article className="story-card" key={post.id}>
              <div className="meta">
                {formatDate(post.publishedAt)} | {post.authorDisplayName}
                {post.commentCount > 0 ? ` | ${post.commentCount} comment${post.commentCount == 1 ? "" : "s"}` : ""}
              </div>
              <h2 className="story-title">
                <Link href={`/posts/${post.slug}`}>{post.title}</Link>
              </h2>
              {post.excerpt ? <p className="story-excerpt">{post.excerpt}</p> : null}
              {post.tags.length > 0 ? (
                <div className="tags">
                  {post.tags.map((tag) => (
                    <span className="tag" key={`${post.id}-${tag}`}>
                      {tag}
                    </span>
                  ))}
                </div>
              ) : null}
            </article>
          ))}
        </section>
      )}

      <div className="pagination">
        <div>{safePage > 0 ? <Link className="pagelink" href={`/?page=${safePage - 1}`}>Previous</Link> : null}</div>
        <div>{safePage + 1 < list.totalPages ? <Link className="pagelink" href={`/?page=${safePage + 1}`}>Next</Link> : null}</div>
      </div>
    </>
  );
}
