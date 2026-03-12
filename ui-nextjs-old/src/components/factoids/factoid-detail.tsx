"use client";

import { useEffect, useState } from "react";
import { FactoidDetail as FactoidDetailType } from "@/lib/api/types";
import { getFactoid } from "@/lib/api/factoids";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";

interface FactoidDetailProps {
  selector: string;
}

export function FactoidDetailView({ selector }: FactoidDetailProps) {
  const [factoid, setFactoid] = useState<FactoidDetailType | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getFactoid(selector)
      .then(setFactoid)
      .catch(() => setError("Failed to load factoid."))
      .finally(() => setLoading(false));
  }, [selector]);

  if (loading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-32 w-full" />
      </div>
    );
  }

  if (error || !factoid) {
    return (
      <p className="text-muted-foreground">{error || "Factoid not found."}</p>
    );
  }

  return (
    <div>
      <div className="mb-6 flex items-center gap-3">
        <h1 className="text-3xl font-bold">{factoid.selector}</h1>
        {factoid.locked && <Badge variant="secondary">locked</Badge>}
      </div>

      <p className="text-muted-foreground mb-6 text-sm">
        {factoid.updatedBy && `Updated by ${factoid.updatedBy} · `}
        {new Date(factoid.updatedAt).toLocaleDateString("en-US", {
          year: "numeric",
          month: "long",
          day: "numeric",
        })}
      </p>

      <Separator className="mb-6" />

      <div className="grid gap-4">
        {factoid.attributes.map((attr, i) => (
          <Card key={i}>
            <CardHeader>
              <CardTitle className="text-base">{attr.type}</CardTitle>
            </CardHeader>
            <CardContent>
              <div
                className="prose dark:prose-invert prose-sm max-w-none"
                dangerouslySetInnerHTML={{ __html: attr.rendered }}
              />
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
