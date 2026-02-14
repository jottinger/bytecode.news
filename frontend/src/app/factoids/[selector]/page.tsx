"use client";

import { use } from "react";
import { FactoidDetailView } from "@/components/factoids/factoid-detail";

interface FactoidPageProps {
  params: Promise<{
    selector: string;
  }>;
}

export default function FactoidPage({ params }: FactoidPageProps) {
  const { selector } = use(params);

  return (
    <div className="container max-w-screen-lg py-12">
      <FactoidDetailView selector={decodeURIComponent(selector)} />
    </div>
  );
}
