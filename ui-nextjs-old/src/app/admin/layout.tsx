"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useRequireAdmin } from "@/lib/auth/hooks";
import { Role } from "@/lib/api/types";
import { cn } from "@/lib/utils";

const adminLinks = [
  { href: "/admin", label: "Dashboard" },
  { href: "/admin/posts", label: "Posts" },
  { href: "/admin/categories", label: "Categories" },
  { href: "/admin/users", label: "Users" },
];

export default function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { user, isLoading } = useRequireAdmin();
  const pathname = usePathname();

  if (
    isLoading ||
    !user ||
    (user.role !== Role.ADMIN && user.role !== Role.SUPER_ADMIN)
  ) {
    return null;
  }

  return (
    <div className="container max-w-screen-lg py-12">
      <h1 className="mb-8 text-3xl font-bold">Admin</h1>
      <div className="flex flex-col gap-8 md:flex-row">
        <nav className="flex gap-2 md:w-48 md:shrink-0 md:flex-col">
          {adminLinks.map((link) => {
            const isActive =
              link.href === "/admin"
                ? pathname === "/admin"
                : pathname.startsWith(link.href);
            return (
              <Link
                key={link.href}
                href={link.href}
                className={cn(
                  "rounded-md px-3 py-2 text-sm font-medium transition-colors",
                  isActive
                    ? "bg-primary text-primary-foreground"
                    : "text-muted-foreground hover:bg-muted hover:text-foreground"
                )}
              >
                {link.label}
              </Link>
            );
          })}
        </nav>
        <div className="min-w-0 flex-1">{children}</div>
      </div>
    </div>
  );
}
