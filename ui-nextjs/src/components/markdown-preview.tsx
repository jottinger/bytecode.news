import ReactMarkdown from "react-markdown";
import rehypeHighlight from "rehype-highlight";
import remarkGfm from "remark-gfm";
import remarkWikiLink from "@flowershow/remark-wiki-link";

interface MarkdownPreviewProps {
  source: string;
  className?: string;
}

const FACTOID_LABEL_WIKILINK = /\[\[([^[\]|]+?)\|([^[\]|]+?)\]\]/g;

function normalizeFactoidLabelSyntax(source: string): string {
  return source.replace(FACTOID_LABEL_WIKILINK, (_match, text: string, selector: string) => {
    const display = text.trim();
    const normalizedSelector = selector.trim();
    if (!display || !normalizedSelector) return _match;
    return `[[${normalizedSelector}|${display}]]`;
  });
}

function resolveFactoidWikiTarget({
  filePath,
  heading,
}: {
  filePath: string;
  heading?: string;
}): string {
  const target = filePath.trim();
  if (!target) return "/factoids/";
  const encodedSelector = encodeURIComponent(target);
  const normalizedHeading = heading?.trim();
  if (!normalizedHeading) return `/factoids/${encodedSelector}`;
  return `/factoids/${encodedSelector}#${encodeURIComponent(normalizedHeading)}`;
}

export function MarkdownPreview({ source, className }: MarkdownPreviewProps) {
  const normalized = normalizeFactoidLabelSyntax(source);

  return (
    <div className={className}>
      <ReactMarkdown
        remarkPlugins={[
          remarkGfm,
          [remarkWikiLink, { aliasDivider: "|", urlResolver: resolveFactoidWikiTarget }],
        ]}
        rehypePlugins={[rehypeHighlight]}
        components={{
          a: ({ ...props }) => <a {...props} target="_blank" rel="noreferrer" />,
        }}
      >
        {normalized}
      </ReactMarkdown>
    </div>
  );
}
