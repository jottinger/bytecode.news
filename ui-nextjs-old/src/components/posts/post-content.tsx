interface PostContentProps {
  html: string;
}

export function PostContent({ html }: PostContentProps) {
  return (
    <article
      className="prose dark:prose-invert max-w-none"
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
}
