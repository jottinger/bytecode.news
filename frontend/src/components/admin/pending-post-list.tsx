"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { ContentListResponse, ContentSummary } from "@/lib/api/types";
import { approvePost, deletePost, getPendingPosts } from "@/lib/api/admin";
import { ApiError } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";

export function PendingPostList() {
  const [data, setData] = useState<ContentListResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [approvingId, setApprovingId] = useState<string | null>(null);
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await getPendingPosts(page, 10);
      setData(result);
    } catch {
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  async function handleApprove(post: ContentSummary) {
    setApprovingId(post.id);
    try {
      await approvePost(post.id, {
        publishedAt: new Date().toISOString(),
      });
      toast.success(`"${post.title}" approved and published.`);
      fetchData();
    } catch (error) {
      const message =
        error instanceof ApiError
          ? error.detail
          : "Failed to approve post.";
      toast.error(message);
    } finally {
      setApprovingId(null);
    }
  }

  async function handleDelete() {
    if (!deleteId) return;
    setDeleting(true);
    try {
      await deletePost(deleteId);
      toast.success("Post deleted.");
      fetchData();
    } catch (error) {
      const message =
        error instanceof ApiError
          ? error.detail
          : "Failed to delete post.";
      toast.error(message);
    } finally {
      setDeleting(false);
      setDeleteOpen(false);
      setDeleteId(null);
    }
  }

  if (loading) {
    return (
      <div className="space-y-4">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-32 w-full rounded-lg" />
        ))}
      </div>
    );
  }

  if (!data || data.posts.length === 0) {
    return (
      <p className="text-muted-foreground text-sm">No pending posts.</p>
    );
  }

  return (
    <div className="space-y-4">
      {data.posts.map((post) => (
        <Card key={post.id}>
          <CardHeader>
            <div className="flex items-start justify-between gap-4">
              <div>
                <CardTitle className="text-lg">{post.title}</CardTitle>
                <CardDescription>
                  By {post.authorDisplayName} &middot;{" "}
                  {new Date(post.createdAt).toLocaleDateString()}
                </CardDescription>
              </div>
              <Badge variant="secondary">Pending</Badge>
            </div>
          </CardHeader>
          {post.excerpt && (
            <CardContent>
              <p className="text-muted-foreground text-sm">{post.excerpt}</p>
            </CardContent>
          )}
          <CardContent className="flex gap-2">
            <Button
              size="sm"
              onClick={() => handleApprove(post)}
              disabled={approvingId === post.id}
            >
              {approvingId === post.id ? "Approving..." : "Approve"}
            </Button>
            <Dialog
              open={deleteOpen && deleteId === post.id}
              onOpenChange={(open) => {
                setDeleteOpen(open);
                if (!open) setDeleteId(null);
              }}
            >
              <DialogTrigger asChild>
                <Button
                  size="sm"
                  variant="destructive"
                  onClick={() => setDeleteId(post.id)}
                >
                  Delete
                </Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>Delete post?</DialogTitle>
                  <DialogDescription>
                    This will soft-delete &ldquo;{post.title}&rdquo;. This
                    action can be reversed by a database administrator.
                  </DialogDescription>
                </DialogHeader>
                <DialogFooter>
                  <Button
                    variant="ghost"
                    onClick={() => setDeleteOpen(false)}
                  >
                    Cancel
                  </Button>
                  <Button
                    variant="destructive"
                    onClick={handleDelete}
                    disabled={deleting}
                  >
                    {deleting ? "Deleting..." : "Delete"}
                  </Button>
                </DialogFooter>
              </DialogContent>
            </Dialog>
          </CardContent>
        </Card>
      ))}

      {data.totalPages > 1 && (
        <Pagination>
          <PaginationContent>
            {page > 0 && (
              <PaginationItem>
                <PaginationPrevious
                  href="#"
                  onClick={(e) => {
                    e.preventDefault();
                    setPage(page - 1);
                  }}
                />
              </PaginationItem>
            )}
            {Array.from({ length: data.totalPages }).map((_, i) => (
              <PaginationItem key={i}>
                <PaginationLink
                  href="#"
                  isActive={i === page}
                  onClick={(e) => {
                    e.preventDefault();
                    setPage(i);
                  }}
                >
                  {i + 1}
                </PaginationLink>
              </PaginationItem>
            ))}
            {page < data.totalPages - 1 && (
              <PaginationItem>
                <PaginationNext
                  href="#"
                  onClick={(e) => {
                    e.preventDefault();
                    setPage(page + 1);
                  }}
                />
              </PaginationItem>
            )}
          </PaginationContent>
        </Pagination>
      )}
    </div>
  );
}
