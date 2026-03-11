"use client";

import { PendingPostList } from "@/components/admin/pending-post-list";

export default function AdminPostsPage() {
  return (
    <div className="space-y-6">
      <h2 className="text-2xl font-bold">Post Moderation</h2>
      <PendingPostList />
    </div>
  );
}
