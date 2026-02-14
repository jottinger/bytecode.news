"use client";

import { useEffect, useState } from "react";
import { Input } from "@/components/ui/input";

interface FactoidSearchProps {
  value: string;
  onChange: (value: string) => void;
}

export function FactoidSearch({ value, onChange }: FactoidSearchProps) {
  const [local, setLocal] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => {
      if (local !== value) {
        onChange(local);
      }
    }, 300);
    return () => clearTimeout(timer);
  }, [local, value, onChange]);

  useEffect(() => {
    setLocal(value);
  }, [value]);

  return (
    <div className="relative max-w-md">
      <span className="font-mono text-amber-dim pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-xs">
        &gt;
      </span>
      <Input
        placeholder="Search factoids..."
        value={local}
        onChange={(e) => setLocal(e.target.value)}
        className="font-mono pl-7 text-sm"
      />
    </div>
  );
}
