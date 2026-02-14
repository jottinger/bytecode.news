"use client";

import { useState } from "react";
import Link from "next/link";
import { useAuth } from "@/lib/auth/hooks";
import { Role } from "@/lib/api/types";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetTrigger,
  SheetTitle,
} from "@/components/ui/sheet";

const navLinks = [
  { href: "/", label: "Front Page" },
  { href: "/posts", label: "Articles" },
  { href: "/factoids", label: "Factoids" },
];

export function MobileNav() {
  const [open, setOpen] = useState(false);
  const { user } = useAuth();
  const isAdmin =
    user?.role === Role.ADMIN || user?.role === Role.SUPER_ADMIN;

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button
          variant="ghost"
          className="mr-2 px-0 text-base hover:bg-transparent focus-visible:bg-transparent focus-visible:ring-0 focus-visible:ring-offset-0 md:hidden"
        >
          <svg
            strokeWidth="1.5"
            viewBox="0 0 24 24"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            className="h-5 w-5"
          >
            <path
              d="M3 5H11"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
            <path
              d="M3 12H16"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
            <path
              d="M3 19H21"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
          <span className="sr-only">Toggle Menu</span>
        </Button>
      </SheetTrigger>
      <SheetContent side="left" className="pr-0">
        <SheetTitle>
          <Link
            href="/"
            className="flex items-center"
            onClick={() => setOpen(false)}
          >
            <span className="font-display text-lg">
              <span className="text-foreground">bytecode</span>
              <span className="text-amber">.</span>
              <span className="text-foreground/50">news</span>
            </span>
          </Link>
        </SheetTitle>
        <div className="border-t border-amber/30 mt-4 mb-2" />
        <nav className="mt-4 flex flex-col gap-4">
          {navLinks.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="section-label text-muted-foreground hover:text-amber transition-colors"
              onClick={() => setOpen(false)}
            >
              {link.label}
            </Link>
          ))}
          {isAdmin && (
            <Link
              href="/admin"
              className="section-label text-muted-foreground hover:text-amber transition-colors"
              onClick={() => setOpen(false)}
            >
              Admin
            </Link>
          )}
        </nav>
      </SheetContent>
    </Sheet>
  );
}
