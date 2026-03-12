import { Suspense } from "react";
import { LogsBrowser } from "@/components/logs-browser";

export default function LogsPage() {
  return (
    <Suspense fallback={<section className="notice">Loading logs...</section>}>
      <LogsBrowser />
    </Suspense>
  );
}
