"use client";

import Link from "next/link";
import { useAuth } from "@/lib/auth/hooks";
import { UserMenu } from "@/components/auth/user-menu";
import { Button } from "@/components/ui/button";
import { MobileNav } from "./mobile-nav";
import { ThemeToggle } from "./theme-toggle";

const navLinks = [
  { href: "/", label: "Front Page" },
  { href: "/posts", label: "Articles" },
  { href: "/factoids", label: "Factoids" },
  { href: "/logs", label: "Logs" },
];

function formatEditionDate(): string {
  return new Date().toLocaleDateString("en-US", {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
  });
}

export function SiteHeader() {
  const { isAuthenticated, isLoading } = useAuth();

  return (
    <header className="sticky top-0 z-50 w-full">
      <div className="h-[2px] bg-amber" />
      <div className="bg-background/95 supports-[backdrop-filter]:bg-background/80 backdrop-blur border-b border-border/40">
        <div className="container flex h-14 max-w-screen-xl items-center">
          <div className="mr-4 hidden md:flex items-center">
            <Link href="/" className="mr-8 flex items-center">
              <span className="font-display text-xl tracking-tight">
                <span className="text-foreground">bytecode</span>
                <span className="text-amber">.</span>
                <span className="text-foreground/50">news</span>
              </span>
            </Link>
            <div className="h-5 w-px bg-border/60 mr-6" />
            <nav className="flex items-center gap-6">
              {navLinks.map((link) => (
                <Link
                  key={link.href}
                  href={link.href}
                  className="section-label text-muted-foreground hover:text-amber transition-colors duration-200"
                >
                  {link.label}
                </Link>
              ))}
            </nav>
          </div>
          <MobileNav />
          <div className="hidden md:flex flex-1 items-center justify-center">
            <span className="dateline text-muted-foreground/60">
              {formatEditionDate()}
            </span>
          </div>
          <div className="flex flex-1 md:flex-none items-center justify-end space-x-1 md:ml-4">
            <ThemeToggle />
            {!isLoading && (
              <>
                {isAuthenticated ? (
                  <UserMenu />
                ) : (
                  <Button asChild variant="ghost" size="sm" className="section-label text-muted-foreground hover:text-amber h-9 px-3">
                    <Link href="/login">Log in</Link>
                  </Button>
                )}
              </>
            )}
          </div>
        </div>
      </div>
    </header>
  );
}
