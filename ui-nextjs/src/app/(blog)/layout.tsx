export default function BlogLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <div className="container max-w-screen-xl py-16">{children}</div>;
}
