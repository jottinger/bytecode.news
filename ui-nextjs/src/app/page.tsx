"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { PostSummary, SpringPage } from "@/lib/api/types";
import { listPosts } from "@/lib/api/posts";
import { PostCard } from "@/components/posts/post-card";
import { Skeleton } from "@/components/ui/skeleton";

function formatMastheadDate(): string {
  return new Date().toLocaleDateString("en-US", {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
  }).toUpperCase();
}

export default function HomePage() {
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

  const posts = data?.content ?? [];
  const lead = posts[0] ?? null;
  const secondary = posts.slice(1, 3);
  const belowFold = posts.slice(3, 9);

  return (
    <div className="noise-overlay">
      <Masthead />

      <section className="container max-w-screen-xl py-8 md:py-12">
        <SectionHeader label="Latest Dispatches" linkHref="/posts" linkLabel="All Articles" />

        {loading ? (
          <NewspaperSkeleton />
        ) : posts.length > 0 ? (
          <div className="animate-press-in">
            <div className="grid grid-cols-1 md:grid-cols-5 gap-0">
              <div className="md:col-span-3 md:border-r md:border-border/30 md:pr-8">
                <PostCard post={lead!} variant="lead" />
              </div>

              {secondary.length > 0 && (
                <div className="md:col-span-2 md:pl-8 mt-8 md:mt-0">
                  {secondary.map((post, i) => (
                    <div
                      key={post.id}
                      className={i > 0 ? "border-t border-border/30 pt-6 mt-6" : ""}
                    >
                      <PostCard post={post} variant="secondary" />
                    </div>
                  ))}
                </div>
              )}
            </div>

            {belowFold.length > 0 && (
              <>
                <div className="my-10 relative">
                  <div className="border-t-2 border-amber/20" />
                  <span className="section-label text-amber/40 absolute left-0 -top-2.5 bg-background px-2 pl-0">
                    More Stories
                  </span>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-8 gap-y-0">
                  {belowFold.map((post, i) => (
                    <div
                      key={post.id}
                      className="border-t border-border/20 py-6 first:border-t-0 sm:[&:nth-child(-n+2)]:border-t-0 lg:[&:nth-child(-n+3)]:border-t-0"
                      style={{ animationDelay: `${(i + 3) * 80}ms` }}
                    >
                      <PostCard post={post} variant="brief" />
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        ) : (
          <EmptyEdition />
        )}
      </section>
    </div>
  );
}

function Masthead() {
  return (
    <section className="border-b border-border/30">
      <div className="container max-w-screen-xl">
        <div className="border-t-[3px] border-amber animate-rule-draw" />

        <div className="py-10 md:py-14 text-center">
          <p className="dateline text-amber-dim/70 mb-4 animate-fade-in" style={{ animationDelay: "100ms" }}>
            {formatMastheadDate()}
          </p>

          <h1
            className="masthead-name text-foreground animate-press-in"
            style={{ animationDelay: "200ms" }}
          >
            bytecode<span className="text-amber">.</span>news
          </h1>

          <div className="flex items-center justify-center gap-4 mt-5 animate-fade-in" style={{ animationDelay: "400ms" }}>
            <div className="h-px w-12 bg-amber/20" />
            <p className="section-label text-muted-foreground/50">
              Programming News &amp; Technical Writing
            </p>
            <div className="h-px w-12 bg-amber/20" />
          </div>
        </div>

        <div className="border-t border-amber/30" />
        <div className="border-t border-amber/30 mt-px" />
      </div>
    </section>
  );
}

function SectionHeader({
  label,
  linkHref,
  linkLabel,
}: {
  label: string;
  linkHref?: string;
  linkLabel?: string;
}) {
  return (
    <div className="flex items-center gap-4 mb-8">
      <span className="section-label text-amber font-medium">
        {label}
      </span>
      <div className="flex-1 border-t border-border/40" />
      {linkHref && linkLabel && (
        <Link
          href={linkHref}
          className="section-label text-muted-foreground/50 hover:text-amber transition-colors flex items-center gap-1.5"
        >
          {linkLabel}
          <span className="text-[0.5rem]">&rarr;</span>
        </Link>
      )}
    </div>
  );
}

function EmptyEdition() {
  return (
    <div className="animate-fade-in py-16 max-w-md mx-auto text-center">
      <div className="border-t-2 border-amber/30 mb-8" />
      <p className="section-label text-amber/60 mb-4">Notice</p>
      <p className="font-display text-2xl text-foreground/80 tracking-tight leading-snug">
        The editorial team is preparing the first edition.
      </p>
      <p className="text-muted-foreground/50 text-sm mt-4 leading-relaxed">
        Articles will appear here once published. Check back soon for
        technical writing, engineering insights, and dispatches from the
        world of software.
      </p>
      <div className="border-t-2 border-amber/30 mt-8" />
    </div>
  );
}

function NewspaperSkeleton() {
  return (
    <div className="grid grid-cols-1 md:grid-cols-5 gap-0">
      <div className="md:col-span-3 md:border-r md:border-border/20 md:pr-8">
        <Skeleton className="h-3 w-32 mb-4" />
        <Skeleton className="h-10 w-full mb-2" />
        <Skeleton className="h-10 w-3/4 mb-4" />
        <Skeleton className="h-4 w-full mb-2" />
        <Skeleton className="h-4 w-5/6 mb-2" />
        <Skeleton className="h-4 w-2/3" />
      </div>
      <div className="md:col-span-2 md:pl-8 mt-8 md:mt-0 space-y-8">
        <div>
          <Skeleton className="h-3 w-24 mb-3" />
          <Skeleton className="h-7 w-full mb-2" />
          <Skeleton className="h-7 w-2/3 mb-3" />
          <Skeleton className="h-4 w-full" />
        </div>
        <div className="border-t border-border/20 pt-8">
          <Skeleton className="h-3 w-24 mb-3" />
          <Skeleton className="h-7 w-full mb-2" />
          <Skeleton className="h-7 w-1/2 mb-3" />
          <Skeleton className="h-4 w-5/6" />
        </div>
      </div>
    </div>
  );
}
