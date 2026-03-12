import { Suspense } from "react";
import { FactoidList } from "@/components/factoids/factoid-list";
import { Skeleton } from "@/components/ui/skeleton";

export default function FactoidsPage() {
  return (
    <div className="container max-w-screen-xl py-16">
      <div className="mb-10">
        <h1 className="font-display text-4xl tracking-tight">Factoids</h1>
        <div className="bg-amber mt-2 h-px w-12" />
        <p className="text-muted-foreground mt-4 max-w-lg text-sm leading-relaxed">
          Community-curated knowledge fragments. Search, browse, and discover.
        </p>
      </div>
      <Suspense
        fallback={
          <div className="space-y-4">
            <Skeleton className="h-10 w-72 rounded-md" />
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {Array.from({ length: 6 }).map((_, i) => (
                <Skeleton key={i} className="h-24 w-full rounded-md" />
              ))}
            </div>
          </div>
        }
      >
        <FactoidList />
      </Suspense>
    </div>
  );
}
