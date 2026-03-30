"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const adminLinks = [
  { href: "/admin", label: "Dashboard" },
  { href: "/admin/pending", label: "Pending Drafts" },
  { href: "/admin/users", label: "Users" },
  { href: "/admin/categories", label: "Categories" },
];

export function AdminNav() {
  const pathname = usePathname();

  return (
    <nav className="flex gap-0 border-b border-border/40">
      {adminLinks.map((link) => {
        const active = link.href === "/admin"
          ? pathname === "/admin"
          : pathname.startsWith(link.href);

        return (
          <Link
            key={link.href}
            href={link.href}
            className={`section-label px-4 py-3 -mb-px transition-colors whitespace-nowrap ${
              active
                ? "text-amber border-b-2 border-amber"
                : "text-muted-foreground hover:text-foreground"
            }`}
          >
            {link.label}
          </Link>
        );
      })}
    </nav>
  );
}
