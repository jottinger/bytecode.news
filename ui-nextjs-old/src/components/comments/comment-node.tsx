"use client";

import { useState } from "react";
import { CommentNode as CommentNodeType } from "@/lib/api/types";
import { useAuth } from "@/lib/auth/hooks";
import { CommentForm } from "./comment-form";
import { CommentEditForm } from "./comment-edit-form";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";

interface CommentNodeProps {
  comment: CommentNodeType;
  year: string;
  month: string;
  slug: string;
  depth?: number;
  onRefresh: () => void;
}

export function CommentNodeComponent({
  comment,
  year,
  month,
  slug,
  depth = 0,
  onRefresh,
}: CommentNodeProps) {
  const { isAuthenticated } = useAuth();
  const [showReply, setShowReply] = useState(false);
  const [showEdit, setShowEdit] = useState(false);

  const timeAgo = formatTimeAgo(comment.createdAt);

  if (comment.deleted) {
    return (
      <div className={depth > 0 ? "ml-6 border-l pl-4" : ""}>
        <p className="text-muted-foreground py-2 text-sm italic">
          [deleted]
        </p>
        {comment.children.map((child) => (
          <CommentNodeComponent
            key={child.id}
            comment={child}
            year={year}
            month={month}
            slug={slug}
            depth={depth + 1}
            onRefresh={onRefresh}
          />
        ))}
      </div>
    );
  }

  return (
    <div className={depth > 0 ? "ml-6 border-l pl-4" : ""}>
      <div className="py-3">
        <div className="mb-1 flex items-center gap-2 text-sm">
          <span className="font-medium">{comment.authorDisplayName}</span>
          <span className="text-muted-foreground">{timeAgo}</span>
          {comment.editable && (
            <Button
              variant="ghost"
              size="sm"
              className="h-auto px-1 py-0 text-xs"
              onClick={() => setShowEdit(true)}
            >
              edit
            </Button>
          )}
        </div>

        {showEdit ? (
          <CommentEditForm
            commentId={comment.id}
            initialContent=""
            onSuccess={() => {
              setShowEdit(false);
              onRefresh();
            }}
            onCancel={() => setShowEdit(false)}
          />
        ) : (
          <div
            className="prose dark:prose-invert prose-sm max-w-none"
            dangerouslySetInnerHTML={{ __html: comment.renderedHtml }}
          />
        )}

        {isAuthenticated && !showEdit && (
          <Button
            variant="ghost"
            size="sm"
            className="text-muted-foreground mt-1 h-auto px-1 py-0 text-xs"
            onClick={() => setShowReply(!showReply)}
          >
            reply
          </Button>
        )}

        {showReply && (
          <div className="mt-2">
            <CommentForm
              year={year}
              month={month}
              slug={slug}
              parentCommentId={comment.id}
              onSuccess={() => {
                setShowReply(false);
                onRefresh();
              }}
              onCancel={() => setShowReply(false)}
            />
          </div>
        )}
      </div>

      {comment.children.map((child) => (
        <CommentNodeComponent
          key={child.id}
          comment={child}
          year={year}
          month={month}
          slug={slug}
          depth={depth + 1}
          onRefresh={onRefresh}
        />
      ))}

      {depth === 0 && <Separator />}
    </div>
  );
}

function formatTimeAgo(dateStr: string): string {
  const date = new Date(dateStr);
  const now = new Date();
  const seconds = Math.floor((now.getTime() - date.getTime()) / 1000);

  if (seconds < 60) return "just now";
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  if (seconds < 2592000) return `${Math.floor(seconds / 86400)}d ago`;
  return date.toLocaleDateString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}
