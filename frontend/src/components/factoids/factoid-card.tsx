import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { FactoidSummary } from "@/lib/api/types";

interface FactoidCardProps {
  factoid: FactoidSummary;
}

export function FactoidCard({ factoid }: FactoidCardProps) {
  return (
    <Link
      href={`/factoids/${encodeURIComponent(factoid.selector)}`}
      className="group block"
    >
      <div className="border-border/40 hover:border-amber/30 relative rounded-md border p-5 transition-all duration-300 hover:shadow-[0_0_20px_-8px] hover:shadow-amber/10">
        <div className="bg-amber absolute left-0 top-0 bottom-0 w-px opacity-0 transition-opacity duration-300 group-hover:opacity-100" />
        <div className="flex items-start justify-between gap-2">
          <h3 className="font-mono text-sm font-medium leading-tight group-hover:text-amber transition-colors duration-200">
            {factoid.selector}
          </h3>
          {factoid.locked && (
            <Badge
              variant="outline"
              className="border-amber-dim/30 text-amber-dim shrink-0 text-[10px]"
            >
              locked
            </Badge>
          )}
        </div>
        <p className="font-mono text-muted-foreground mt-2 text-xs">
          {factoid.updatedBy && (
            <span>{factoid.updatedBy} &middot; </span>
          )}
          {new Date(factoid.updatedAt).toLocaleDateString("en-US", {
            year: "numeric",
            month: "short",
            day: "numeric",
          })}
        </p>
      </div>
    </Link>
  );
}
