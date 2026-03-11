"use client";

import { useCallback, useEffect, useState } from "react";
import { CommentThreadResponse } from "@/lib/api/types";
import { getComments } from "@/lib/api/comments";
import { useAuth } from "@/lib/auth/hooks";
import { CommentNodeComponent } from "./comment-node";
import { CommentForm } from "./comment-form";
import { Skeleton } from "@/components/ui/skeleton";

interface CommentThreadProps {
  year: string;
  month: string;
  slug: string;
}

export function CommentThread({ year, month, slug }: CommentThreadProps) {
  const { isAuthenticated } = useAuth();
  const [thread, setThread] = useState<CommentThreadResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchComments = useCallback(async () => {
    try {
      const data = await getComments(year, month, slug);
      setThread(data);
      setError(null);
    } catch {
      setError("Failed to load comments.");
    } finally {
      setLoading(false);
    }
  }, [year, month, slug]);

  useEffect(() => {
    fetchComments();
  }, [fetchComments]);

  if (loading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-6 w-32" />
        <Skeleton className="h-20 w-full" />
        <Skeleton className="h-20 w-full" />
      </div>
    );
  }

  if (error) {
    return <p className="text-muted-foreground text-sm">{error}</p>;
  }

  return (
    <section className="mt-8">
      <h2 className="mb-4 text-xl font-semibold">
        Comments
        {thread && thread.totalActiveCount > 0 && (
          <span className="text-muted-foreground ml-2 text-base font-normal">
            ({thread.totalActiveCount})
          </span>
        )}
      </h2>

      {isAuthenticated && (
        <div className="mb-6">
          <CommentForm
            year={year}
            month={month}
            slug={slug}
            onSuccess={fetchComments}
          />
        </div>
      )}

      {!isAuthenticated && (
        <p className="text-muted-foreground mb-6 text-sm">
          Sign in to leave a comment.
        </p>
      )}

      {thread && thread.comments.length > 0 ? (
        <div>
          {thread.comments.map((comment) => (
            <CommentNodeComponent
              key={comment.id}
              comment={comment}
              year={year}
              month={month}
              slug={slug}
              onRefresh={fetchComments}
            />
          ))}
        </div>
      ) : (
        <p className="text-muted-foreground text-sm">
          No comments yet. Be the first to comment.
        </p>
      )}
    </section>
  );
}
