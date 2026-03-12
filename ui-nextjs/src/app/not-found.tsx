import Link from "next/link";

export default function NotFound() {
  return (
    <section className="notice">
      <h2>Not Found</h2>
      <p>That resource does not exist.</p>
      <p>
        <Link href="/">Return home</Link>
      </p>
    </section>
  );
}
