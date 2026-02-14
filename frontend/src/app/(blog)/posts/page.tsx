"use client";

import { useCallback, useEffect, useState } from "react";
import { PostSummary, SpringPage } from "@/lib/api/types";
import { listPosts } from "@/lib/api/posts";
import { PostCard } from "@/components/posts/post-card";
import { Skeleton } from "@/components/ui/skeleton";

export default function PostsPage() {
  const [data, setData] = useState<SpringPage<PostSummary> | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchPosts = useCallback(async () => {
    setLoading(true);
    try {
      const result = await listPosts();
      setData(result);
    } catch {
      setData(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchPosts();
  }, [fetchPosts]);

  return (
    <div>
      <div className="mb-10">
        <div className="border-t-2 border-amber mb-6 w-16 animate-rule-draw" />
        <h1 className="font-display text-4xl md:text-5xl tracking-tight">
          Articles
        </h1>
        <p className="section-label text-muted-foreground/50 mt-3">
          All published dispatches
        </p>
      </div>

      {loading ? (
        <div className="space-y-0">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="border-t border-border/30 py-6 first:border-t-0">
              <div className="flex items-center gap-3 mb-3">
                <Skeleton className="h-2.5 w-24" />
                <Skeleton className="h-2.5 w-16" />
              </div>
              <Skeleton className="mb-2 h-7 w-3/4" />
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-2/3 mt-1" />
            </div>
          ))}
        </div>
      ) : data && data.content.length > 0 ? (
        <div>
          {data.content.map((post, i) => (
            <div
              key={post.id}
              className="animate-fade-up"
              style={{ animationDelay: `${i * 60}ms` }}
            >
              <PostCard post={post} />
            </div>
          ))}
        </div>
      ) : (
        <div className="animate-fade-in py-16 max-w-sm mx-auto text-center">
          <div className="border-t border-amber/30 mb-6" />
          <p className="section-label text-amber/50 mb-3">Notice</p>
          <p className="font-display text-xl text-foreground/70 tracking-tight">
            No articles published yet.
          </p>
          <p className="text-muted-foreground/40 text-sm mt-3">
            Check back soon.
          </p>
          <div className="border-t border-amber/30 mt-6" />
        </div>
      )}
    </div>
  );
}
