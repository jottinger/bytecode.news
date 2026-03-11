"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { getPendingPosts } from "@/lib/api/admin";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

export default function AdminDashboardPage() {
  const [pendingCount, setPendingCount] = useState<number | null>(null);

  const fetchPendingCount = useCallback(async () => {
    try {
      const result = await getPendingPosts(0, 1);
      setPendingCount(result.totalCount);
    } catch {
      setPendingCount(0);
    }
  }, []);

  useEffect(() => {
    fetchPendingCount();
  }, [fetchPendingCount]);

  return (
    <div className="space-y-6">
      <h2 className="text-2xl font-bold">Dashboard</h2>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Link href="/admin/posts">
          <Card className="transition-colors hover:border-foreground/20">
            <CardHeader>
              <CardTitle>Posts</CardTitle>
              <CardDescription>Moderate pending posts</CardDescription>
            </CardHeader>
            <CardContent>
              {pendingCount === null ? (
                <Skeleton className="h-6 w-24" />
              ) : (
                <p className="text-2xl font-semibold">
                  {pendingCount} pending
                </p>
              )}
            </CardContent>
          </Card>
        </Link>
        <Link href="/admin/categories">
          <Card className="transition-colors hover:border-foreground/20">
            <CardHeader>
              <CardTitle>Categories</CardTitle>
              <CardDescription>Manage post categories</CardDescription>
            </CardHeader>
            <CardContent>
              <p className="text-muted-foreground text-sm">
                Create and delete categories
              </p>
            </CardContent>
          </Card>
        </Link>
        <Link href="/admin/users">
          <Card className="transition-colors hover:border-foreground/20">
            <CardHeader>
              <CardTitle>Users</CardTitle>
              <CardDescription>Manage user roles and passwords</CardDescription>
            </CardHeader>
            <CardContent>
              <p className="text-muted-foreground text-sm">
                Change roles and reset passwords
              </p>
            </CardContent>
          </Card>
        </Link>
      </div>
    </div>
  );
}
