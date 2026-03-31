"use client";

import Link from "next/link";
import { getAuthState } from "@/lib/client-auth";

interface PostActionsProps {
  postId: string;
}

export function PostActions({ postId }: PostActionsProps) {
  const auth = getAuthState();
  const isAdmin = auth.principal?.role === "ADMIN" || auth.principal?.role === "SUPER_ADMIN";
  if (!auth.principal || !isAdmin) return null;

  return (
    <p>
      <Link className="pagelink" href={`/edit/${postId}`}>
        Edit post
      </Link>
    </p>
  );
}
