"use client";

import { useEffect, useRef } from "react";
import hljs from "highlight.js";

interface HighlightedHtmlProps {
  html: string;
  className?: string;
}

export function HighlightedHtml({ html, className }: HighlightedHtmlProps) {
  const rootRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const root = rootRef.current;
    if (!root) return;
    root.querySelectorAll("pre code").forEach((block) => {
      hljs.highlightElement(block as HTMLElement);
    });
  }, [html]);

  return <div ref={rootRef} className={className} dangerouslySetInnerHTML={{ __html: html }} />;
}
