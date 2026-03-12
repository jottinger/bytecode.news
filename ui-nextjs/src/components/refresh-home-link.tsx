"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import type { ReactNode, MouseEvent } from "react";

interface RefreshHomeLinkProps {
  className?: string;
  children: ReactNode;
}

export function RefreshHomeLink({ className, children }: RefreshHomeLinkProps) {
  const pathname = usePathname();
  const router = useRouter();

  function onClick(event: MouseEvent<HTMLAnchorElement>) {
    if (pathname === "/") {
      event.preventDefault();
      router.refresh();
    }
  }

  return (
    <Link href="/" className={className} onClick={onClick}>
      {children}
    </Link>
  );
}
