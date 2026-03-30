import { AdminNav } from "@/components/admin-nav";

export default function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <section className="py-8 md:py-12">
      <div className="max-w-4xl mx-auto px-6 md:px-8">
        <header className="mb-8">
          <div className="border-t-2 border-amber animate-rule-draw" />
          <div className="pt-6 pb-6">
            <p className="section-label text-amber mb-3">Administration</p>
            <h1
              className="font-display text-foreground leading-tight"
              style={{
                fontSize: "clamp(2rem, 5vw, 3rem)",
                letterSpacing: "-0.025em",
              }}
            >
              Site Management
            </h1>
          </div>
          <AdminNav />
        </header>
        {children}
      </div>
    </section>
  );
}
