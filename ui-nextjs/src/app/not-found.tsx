import Link from "next/link";

export default function NotFound() {
  return (
    <section className="container max-w-screen-xl py-16">
      <div className="max-w-md mx-auto text-center animate-fade-in">
        <div className="border-t-2 border-amber/30 mb-8" />
        <p className="section-label text-amber/60 mb-4">Not Found</p>
        <p className="font-display text-2xl text-foreground/80 tracking-tight leading-snug">
          That resource does not exist.
        </p>
        <p className="text-muted-foreground/50 text-sm mt-4">
          <Link href="/" className="text-amber hover:text-amber-dim transition-colors underline">
            Return to the front page
          </Link>
        </p>
        <div className="border-t-2 border-amber/30 mt-8" />
      </div>
    </section>
  );
}
