"use client";

import { use } from "react";
import { CommentThread } from "@/components/comments/comment-thread";
import { Separator } from "@/components/ui/separator";

interface PostPageProps {
  params: Promise<{
    year: string;
    month: string;
    slug: string;
  }>;
}

export default function PostPage({ params }: PostPageProps) {
  const { year, month, slug } = use(params);
  const title = slug.replace(/-/g, " ");

  return (
    <article>
      <header className="mb-10">
        <div className="border-t-2 border-amber mb-6 w-12 animate-rule-draw" />
        <h1 className="font-display text-3xl md:text-4xl lg:text-5xl tracking-tight leading-tight mb-4">
          {title}
        </h1>
        <p className="byline text-muted-foreground">
          Article content is not yet available. The full text will appear here
          once the backend post endpoints are implemented.
        </p>
      </header>

      <div className="border border-border/30 py-12 text-center">
        <p className="section-label text-muted-foreground/40 mb-2">
          Content Placeholder
        </p>
        <p className="text-muted-foreground/30 text-sm">
          Article body will render here
        </p>
      </div>

      <Separator className="my-10" />

      <CommentThread year={year} month={month} slug={slug} />
    </article>
  );
}
