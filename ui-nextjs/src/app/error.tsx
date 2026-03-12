"use client";

export default function ErrorPage() {
  return (
    <section className="container max-w-screen-xl py-16">
      <div className="max-w-md mx-auto text-center animate-fade-in">
        <div className="border-t-2 border-amber/30 mb-8" />
        <p className="section-label text-amber/60 mb-4">Error</p>
        <p className="font-display text-2xl text-foreground/80 tracking-tight leading-snug">
          Something went wrong while rendering this page.
        </p>
        <div className="border-t-2 border-amber/30 mt-8" />
      </div>
    </section>
  );
}
