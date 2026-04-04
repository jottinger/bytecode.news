import ReactMarkdown from "react-markdown";
import rehypeHighlight from "rehype-highlight";
import remarkGfm from "remark-gfm";
import remarkWikiLink from "@flowershow/remark-wiki-link";
import type { ReactNode } from "react";

interface MarkdownPreviewProps {
  source: string;
  className?: string;
}

const FACTOID_LABEL_WIKILINK = /\[\[([^[\]|]+?)\|([^[\]|]+?)\]\]/g;
const ADMONITION_MARKER =
  /^(\s*)(!!!|\?\?\?\+?)[ \t]+([A-Za-z][\w-]*)(?:[ \t]+"([^"]+)")?\s*$/;

type MarkdownSegment =
  | { kind: "markdown"; content: string }
  | {
      kind: "admonition";
      admonitionKind: string;
      title: string;
      stateClass?: "adm-collapsed" | "adm-open";
      body: string;
    };

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

function capitalize(value: string): string {
  if (!value) return value;
  return value.charAt(0).toUpperCase() + value.slice(1);
}

function dedentAdmonitionLine(line: string, indent: string): string {
  if (line.trim() === "") return "";
  if (line.startsWith(indent + "\t")) return line.slice(indent.length + 1);
  if (line.startsWith(indent + "    ")) return line.slice(indent.length + 4);
  return line.slice(indent.length);
}

function parseAdmonitionSegments(source: string): MarkdownSegment[] {
  const lines = source.split("\n");
  const segments: MarkdownSegment[] = [];
  let markdownBuffer: string[] = [];
  let index = 0;

  const flushMarkdown = () => {
    const content = markdownBuffer.join("\n").trim();
    if (content) segments.push({ kind: "markdown", content });
    markdownBuffer = [];
  };

  while (index < lines.length) {
    const line = lines[index];
    const match = line.match(ADMONITION_MARKER);

    if (!match) {
      markdownBuffer.push(line);
      index += 1;
      continue;
    }

    const [, indent = "", marker = "!!!", rawKind = "note", rawTitle = ""] = match;
    const admonitionKind = rawKind.toLowerCase();
    const title = rawTitle.trim() || capitalize(admonitionKind);
    const bodyLines: string[] = [];
    let cursor = index + 1;

    while (cursor < lines.length) {
      const candidate = lines[cursor];
      if (candidate.trim() === "") {
        bodyLines.push("");
        cursor += 1;
        continue;
      }

      const isIndented =
        candidate.startsWith(indent + "    ") || candidate.startsWith(indent + "\t");
      if (!isIndented) break;

      bodyLines.push(dedentAdmonitionLine(candidate, indent));
      cursor += 1;
    }

    if (bodyLines.length === 0) {
      markdownBuffer.push(line);
      index += 1;
      continue;
    }

    flushMarkdown();
    segments.push({
      kind: "admonition",
      admonitionKind,
      title,
      stateClass:
        marker === "???"
          ? "adm-collapsed"
          : marker === "???+"
            ? "adm-open"
            : undefined,
      body: bodyLines.join("\n").trim(),
    });
    index = cursor;
  }

  flushMarkdown();
  return segments;
}

function MarkdownBody({ source }: { source: string }) {
  return (
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
      {source}
    </ReactMarkdown>
  );
}

function renderSegment(segment: MarkdownSegment, key: string): ReactNode {
  if (segment.kind === "markdown") {
    return <MarkdownBody key={key} source={segment.content} />;
  }

  const classes = ["adm-block", `adm-${segment.admonitionKind}`];
  if (segment.stateClass) classes.push(segment.stateClass);

  return (
    <div key={key} className={classes.join(" ")}>
      <div className="adm-heading">
        <svg className="adm-icon" viewBox="0 0 16 16" aria-hidden="true">
          <circle cx="8" cy="8" r="7" />
        </svg>
        <span>{segment.title}</span>
      </div>
      <div className="adm-body">
        <MarkdownBody source={segment.body} />
      </div>
    </div>
  );
}

export function MarkdownPreview({ source, className }: MarkdownPreviewProps) {
  const normalized = normalizeFactoidLabelSyntax(source);
  const segments = parseAdmonitionSegments(normalized);

  return (
    <div className={className}>
      {segments.map((segment, index) => renderSegment(segment, `segment-${index}`))}
    </div>
  );
}
