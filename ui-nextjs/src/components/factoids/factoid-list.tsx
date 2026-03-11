"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { FactoidSummary, SpringPage } from "@/lib/api/types";
import { listFactoids } from "@/lib/api/factoids";
import { FactoidCard } from "./factoid-card";
import { FactoidSearch } from "./factoid-search";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";

export function FactoidList() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const q = searchParams.get("q") || "";
  const page = parseInt(searchParams.get("page") || "0", 10);

  const [data, setData] = useState<SpringPage<FactoidSummary> | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await listFactoids({ q: q || undefined, page, size: 20 });
      setData(result);
    } catch {
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [q, page]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  function updateParams(newQ: string, newPage?: number) {
    const params = new URLSearchParams();
    if (newQ) params.set("q", newQ);
    if (newPage && newPage > 0) params.set("page", String(newPage));
    const query = params.toString();
    router.push(`/factoids${query ? `?${query}` : ""}`);
  }

  return (
    <div className="space-y-8">
      <FactoidSearch
        value={q}
        onChange={(newQ) => updateParams(newQ, 0)}
      />

      {loading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-24 w-full rounded-md" />
          ))}
        </div>
      ) : data && data.content.length > 0 ? (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {data.content.map((factoid, i) => (
              <div
                key={factoid.selector}
                className="animate-fade-up"
                style={{ animationDelay: `${i * 40}ms` }}
              >
                <FactoidCard factoid={factoid} />
              </div>
            ))}
          </div>

          {data.totalPages > 1 && (
            <Pagination>
              <PaginationContent>
                {!data.first && (
                  <PaginationItem>
                    <PaginationPrevious
                      href="#"
                      onClick={(e) => {
                        e.preventDefault();
                        updateParams(q, page - 1);
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
                        updateParams(q, i);
                      }}
                    >
                      {i + 1}
                    </PaginationLink>
                  </PaginationItem>
                ))}
                {!data.last && (
                  <PaginationItem>
                    <PaginationNext
                      href="#"
                      onClick={(e) => {
                        e.preventDefault();
                        updateParams(q, page + 1);
                      }}
                    />
                  </PaginationItem>
                )}
              </PaginationContent>
            </Pagination>
          )}
        </>
      ) : (
        <div className="border-border/40 animate-fade-in rounded-md border border-dashed px-8 py-12 text-center">
          <p className="font-mono text-muted-foreground text-sm">
            {q ? `No factoids found for "${q}".` : "No factoids found."}
          </p>
        </div>
      )}
    </div>
  );
}
