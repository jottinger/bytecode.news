import Link from "next/link";

export function SiteFooter() {
  const year = new Date().getFullYear();

  return (
    <footer className="border-t border-border/40">
      <div className="container max-w-screen-xl">
        <div className="border-t border-amber/30 mt-px" />

        <div className="grid grid-cols-1 md:grid-cols-3 gap-8 py-10">
          <div>
            <span className="font-display text-xl tracking-tight">
              <span className="text-foreground">bytecode</span>
              <span className="text-amber">.</span>
              <span className="text-foreground/40">news</span>
            </span>
            <p className="text-muted-foreground/60 text-sm mt-3 leading-relaxed max-w-xs">
              Technical writing, software engineering insights, and the craft of
              building things that work.
            </p>
          </div>

          <div>
            <h4 className="section-label text-amber mb-4">Sections</h4>
            <nav className="flex flex-col gap-2">
              <Link href="/posts" className="text-muted-foreground hover:text-foreground text-sm transition-colors">
                Articles
              </Link>
              <Link href="/factoids" className="text-muted-foreground hover:text-foreground text-sm transition-colors">
                Factoids
              </Link>
            </nav>
          </div>

          <div>
            <h4 className="section-label text-amber mb-4">Account</h4>
            <nav className="flex flex-col gap-2">
              <Link href="/login" className="text-muted-foreground hover:text-foreground text-sm transition-colors">
                Log in
              </Link>
              <Link href="/register" className="text-muted-foreground hover:text-foreground text-sm transition-colors">
                Register
              </Link>
            </nav>
          </div>
        </div>

        <div className="border-t border-border/40 py-5 flex flex-col sm:flex-row items-center justify-between gap-2">
          <p className="dateline text-muted-foreground/40">
            &copy; {year} bytecode.news
          </p>
          <p className="dateline text-muted-foreground/30">
            All rights reserved
          </p>
        </div>
      </div>
    </footer>
  );
}
