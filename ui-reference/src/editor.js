import EasyMDE from "easymde";
import "easymde/dist/easymde.min.css";
import hljs from "highlight.js/lib/core";
import "highlight.js/styles/github.css";
import "./editor-dark.css";

/* Register languages relevant to a JVM-oriented programming site */
import java from "highlight.js/lib/languages/java";
import kotlin from "highlight.js/lib/languages/kotlin";
import javascript from "highlight.js/lib/languages/javascript";
import typescript from "highlight.js/lib/languages/typescript";
import xml from "highlight.js/lib/languages/xml";
import sql from "highlight.js/lib/languages/sql";
import yaml from "highlight.js/lib/languages/yaml";
import json from "highlight.js/lib/languages/json";
import bash from "highlight.js/lib/languages/bash";
import css from "highlight.js/lib/languages/css";
import python from "highlight.js/lib/languages/python";
import groovy from "highlight.js/lib/languages/groovy";
import scala from "highlight.js/lib/languages/scala";
import clojure from "highlight.js/lib/languages/clojure";
import properties from "highlight.js/lib/languages/properties";
import markdown from "highlight.js/lib/languages/markdown";

hljs.registerLanguage("java", java);
hljs.registerLanguage("kotlin", kotlin);
hljs.registerLanguage("javascript", javascript);
hljs.registerLanguage("js", javascript);
hljs.registerLanguage("typescript", typescript);
hljs.registerLanguage("ts", typescript);
hljs.registerLanguage("xml", xml);
hljs.registerLanguage("html", xml);
hljs.registerLanguage("sql", sql);
hljs.registerLanguage("yaml", yaml);
hljs.registerLanguage("yml", yaml);
hljs.registerLanguage("json", json);
hljs.registerLanguage("bash", bash);
hljs.registerLanguage("shell", bash);
hljs.registerLanguage("sh", bash);
hljs.registerLanguage("css", css);
hljs.registerLanguage("python", python);
hljs.registerLanguage("groovy", groovy);
hljs.registerLanguage("scala", scala);
hljs.registerLanguage("clojure", clojure);
hljs.registerLanguage("properties", properties);
hljs.registerLanguage("markdown", markdown);

/**
 * Apply syntax highlighting to all code blocks within an element.
 * Call after inserting server-rendered HTML into the DOM.
 */
export function highlightCodeBlocks(container) {
  if (!container) return;
  container.querySelectorAll("pre code").forEach((block) => {
    hljs.highlightElement(block);
  });
}

/** Toolbar for full post editing (submit/edit views) */
const POST_TOOLBAR = [
  "bold", "italic", "strikethrough", "|",
  "heading-1", "heading-2", "heading-3", "|",
  "code", "quote", "unordered-list", "ordered-list", "|",
  "link", "horizontal-rule", "table", "|",
  "preview", "side-by-side", "fullscreen", "|",
  "guide",
];

/** Compact toolbar for comment forms */
const COMMENT_TOOLBAR = [
  "bold", "italic", "code", "quote", "link", "|",
  "preview",
];

/**
 * Create an EasyMDE instance on a textarea element.
 *
 * @param {HTMLTextAreaElement} textarea - the textarea to enhance
 * @param {object} opts - options
 * @param {boolean} opts.compact - use the compact comment toolbar (default: false)
 * @param {string} opts.initialValue - pre-fill the editor with this content
 * @param {number} opts.minHeight - minimum editor height in pixels (default: 200 for posts, 100 for comments)
 * @returns {EasyMDE} the editor instance
 */
export function createEditor(textarea, opts = {}) {
  const compact = opts.compact || false;
  const minHeight = opts.minHeight || (compact ? "100px" : "200px");

  const editor = new EasyMDE({
    element: textarea,
    toolbar: compact ? COMMENT_TOOLBAR : POST_TOOLBAR,
    initialValue: opts.initialValue || "",
    spellChecker: false,
    status: compact ? false : ["lines", "words"],
    minHeight,
    renderingConfig: {
      codeSyntaxHighlighting: true,
      hljs,
    },
    placeholder: compact ? "Write a comment..." : "Write your post in Markdown...",
  });

  return editor;
}
