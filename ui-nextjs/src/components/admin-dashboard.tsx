"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { getAuthState } from "@/lib/client-auth";
import { CategorySummary, ContentListResponse, FeaturesResponse, TaxonomySnapshot } from "@/lib/types";

type UserPrincipal = {
  id: string;
  username: string;
  displayName: string;
  role: string;
};

interface DashboardData {
  pendingCount: number | null;
  publishedCount: number | null;
  activeUserCount: number | null;
  suspendedUserCount: number | null;
  categories: CategorySummary[] | null;
  taxonomy: TaxonomySnapshot | null;
  features: FeaturesResponse | null;
}

function StatCard({
  label,
  value,
  href,
}: {
  label: string;
  value: string | number;
  href?: string;
}) {
  const inner = (
    <div className="border border-border/40 p-5 group hover:border-amber/40 transition-colors">
      <p className="section-label text-muted-foreground/60 mb-2">{label}</p>
      <p
        className="font-display text-foreground leading-none"
        style={{ fontSize: "clamp(1.8rem, 4vw, 2.5rem)", letterSpacing: "-0.02em" }}
      >
        {value}
      </p>
    </div>
  );

  if (href) {
    return <Link href={href}>{inner}</Link>;
  }
  return inner;
}

export function AdminDashboard() {
  const [data, setData] = useState<DashboardData>({
    pendingCount: null,
    publishedCount: null,
    activeUserCount: null,
    suspendedUserCount: null,
    categories: null,
    taxonomy: null,
    features: null,
  });
  const [loading, setLoading] = useState(true);

  const auth = getAuthState();
  const isAdmin =
    auth.principal?.role === "ADMIN" || auth.principal?.role === "SUPER_ADMIN";

  useEffect(() => {
    if (!auth.principal || !isAdmin) {
      setLoading(false);
      return;
    }

    async function load() {
      const authedOpts = {
        headers: { Accept: "application/json" },
        credentials: "include" as RequestCredentials,
        cache: "no-store" as RequestCache,
      };

      const results = await Promise.allSettled([
        fetch("/api/admin/posts/pending?page=0&size=1&deleted=false", authedOpts)
          .then((r) => r.json() as Promise<ContentListResponse>),

        fetch("/api/posts?page=0&size=1", {
          headers: { Accept: "application/json" },
          cache: "no-store",
        }).then((r) => r.json() as Promise<ContentListResponse>),

        fetch("/api/admin/users?status=ACTIVE", authedOpts)
          .then((r) => r.json() as Promise<UserPrincipal[]>),

        fetch("/api/admin/users?status=SUSPENDED", authedOpts)
          .then((r) => r.json() as Promise<UserPrincipal[]>),

        fetch("/api/categories", {
          headers: { Accept: "application/json" },
          cache: "no-store",
        }).then((r) => r.json() as Promise<CategorySummary[]>),

        fetch("/api/taxonomy", {
          headers: { Accept: "application/json" },
          cache: "no-store",
        }).then((r) => r.json() as Promise<TaxonomySnapshot>),

        fetch("/api/features", {
          headers: { Accept: "application/json" },
          cache: "no-store",
        }).then((r) => r.json() as Promise<FeaturesResponse>),
      ]);

      setData({
        pendingCount:
          results[0].status === "fulfilled" ? results[0].value.totalCount : null,
        publishedCount:
          results[1].status === "fulfilled" ? results[1].value.totalCount : null,
        activeUserCount:
          results[2].status === "fulfilled" ? results[2].value.length : null,
        suspendedUserCount:
          results[3].status === "fulfilled" ? results[3].value.length : null,
        categories:
          results[4].status === "fulfilled" ? results[4].value : null,
        taxonomy:
          results[5].status === "fulfilled" ? results[5].value : null,
        features:
          results[6].status === "fulfilled" ? results[6].value : null,
      });
      setLoading(false);
    }

    void load();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  if (!auth.principal || !isAdmin) {
    return (
      <div className="py-12 text-center">
        <p className="section-label text-muted-foreground">
          Admin access required.
        </p>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="py-12 text-center">
        <p className="section-label text-muted-foreground/50">
          Loading dashboard...
        </p>
      </div>
    );
  }

  const categoryCounts = data.taxonomy?.categories ?? {};
  const tagEntries = data.taxonomy
    ? Object.entries(data.taxonomy.tags).sort(([, a], [, b]) => b - a)
    : [];

  return (
    <div className="animate-fade-in">
      {/* Stats grid */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-10">
        <StatCard
          label="Pending Drafts"
          value={data.pendingCount ?? "--"}
          href="/admin/pending"
        />
        <StatCard
          label="Published"
          value={data.publishedCount ?? "--"}
        />
        <StatCard
          label="Active Users"
          value={data.activeUserCount ?? "--"}
          href="/admin/users"
        />
        <StatCard
          label="Suspended"
          value={data.suspendedUserCount ?? "--"}
        />
      </div>

      {/* Two-column: Categories + Tags */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-8 mb-10">
        {/* Categories */}
        <div>
          <div className="flex items-center justify-between mb-4 border-b border-border/40 pb-3">
            <h2 className="section-label text-amber">Categories</h2>
            <Link
              href="/admin/categories"
              className="section-label text-muted-foreground hover:text-amber transition-colors"
            >
              Manage
            </Link>
          </div>
          {!data.categories || data.categories.length === 0 ? (
            <p className="text-muted-foreground/50 text-sm">No categories yet.</p>
          ) : (
            <div className="space-y-2">
              {data.categories.map((cat) => (
                <div
                  key={cat.id}
                  className="flex items-center justify-between py-1.5"
                >
                  <div className="truncate mr-4">
                    <Link
                      href={`/category/${encodeURIComponent(cat.name)}`}
                      className="text-sm text-foreground hover:text-amber transition-colors"
                    >
                      {cat.name}
                    </Link>
                    {cat.parentName && (
                      <span className="dateline text-muted-foreground/40 ml-2">
                        in {cat.parentName}
                      </span>
                    )}
                  </div>
                  <span className="dateline text-muted-foreground/50 shrink-0">
                    {categoryCounts[cat.name.toLowerCase()] ?? 0} posts
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Top tags */}
        <div>
          <div className="flex items-center justify-between mb-4 border-b border-border/40 pb-3">
            <h2 className="section-label text-amber">Top Tags</h2>
            <Link
              href="/tags"
              className="section-label text-muted-foreground hover:text-amber transition-colors"
            >
              View All
            </Link>
          </div>
          {tagEntries.length === 0 ? (
            <p className="text-muted-foreground/50 text-sm">No tags yet.</p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {tagEntries.slice(0, 20).map(([name, count]) => (
                <Link
                  key={name}
                  href={`/tags/${encodeURIComponent(name)}`}
                  className="tag hover:border-amber/40 hover:text-amber transition-colors"
                >
                  {name} ({count})
                </Link>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* System info */}
      {data.features && (
        <div className="border-t border-border/40 pt-6">
          <h2 className="section-label text-amber mb-4">System</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-3">
            <div className="flex items-baseline gap-2">
              <span className="dateline text-muted-foreground/50">Site</span>
              <span className="text-sm text-foreground">{data.features.siteName}</span>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="dateline text-muted-foreground/50">Build</span>
              <span className="text-sm text-foreground">
                {data.features.version.commit?.substring(0, 7) ?? "unknown"}
                {data.features.version.branch
                  ? ` (${data.features.version.branch})`
                  : ""}
              </span>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="dateline text-muted-foreground/50">Auth</span>
              <span className="text-sm text-foreground">
                {[
                  data.features.authentication.otp ? "OTP" : null,
                  data.features.authentication.oidc?.google ? "Google" : null,
                  data.features.authentication.oidc?.github ? "GitHub" : null,
                ]
                  .filter(Boolean)
                  .join(", ") || "None"}
              </span>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="dateline text-muted-foreground/50">AI</span>
              <span className="text-sm text-foreground">
                {data.features.ai ? "Enabled" : "Disabled"}
              </span>
            </div>
            {data.features.adapters.length > 0 && (
              <div className="flex items-baseline gap-2 sm:col-span-2">
                <span className="dateline text-muted-foreground/50">Adapters</span>
                <span className="text-sm text-foreground">
                  {data.features.adapters.join(", ")}
                </span>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
