import { renderCommentForm } from "./comment-form.js";
import { isLoggedIn } from "../auth.js";

/** Recursively render a nested comment tree */
export function renderCommentThread(comments, postSlug) {
  if (!comments || comments.length === 0) return "";

  return comments.map((c) => {
    const date = new Date(c.createdAt).toLocaleString("en-US");
    const edited = c.updatedAt !== c.createdAt ? " (edited)" : "";
    const body = c.deleted
      ? "<p><em>[deleted]</em></p>"
      : c.renderedHtml;
    const children = renderCommentThread(c.children || [], postSlug);

    const replyId = "reply-" + c.id;
    const replyButton = (!c.deleted && isLoggedIn())
      ? `<a href="#" class="reply-toggle" data-target="${replyId}">Reply</a>`
      : "";
    const editButton = (c.editable && isLoggedIn())
      ? ` | <a href="#" class="edit-toggle" data-comment-id="${c.id}">Edit</a>`
      : "";

    return `<article style="margin-left:1rem;border-left:2px solid var(--pico-muted-border-color);padding-left:1rem">
      <header>
        <strong>${escapeHtml(c.authorDisplayName)}</strong>
        <small>${date}${edited}</small>
      </header>
      <div class="comment-body" data-comment-id="${c.id}"${c.markdownSource ? ` data-markdown-source="${escapeAttr(c.markdownSource)}"` : ""}>${body}</div>
      <footer>
        ${replyButton}${editButton}
        <div id="${replyId}" hidden></div>
      </footer>
      ${children}
    </article>`;
  }).join("");
}

/** Attach event listeners for reply/edit toggles after rendering */
export function attachCommentHandlers(container, postSlug) {
  container.querySelectorAll(".reply-toggle").forEach((a) => {
    a.addEventListener("click", (e) => {
      e.preventDefault();
      e.stopPropagation();
      const target = document.getElementById(a.dataset.target);
      if (target.hidden) {
        const commentId = a.dataset.target.replace("reply-", "");
        target.hidden = false;
        target.innerHTML = renderCommentForm(postSlug, commentId);
        attachFormHandler(target, postSlug);
      } else {
        target.hidden = true;
        target.innerHTML = "";
      }
    });
  });

  container.querySelectorAll(".edit-toggle").forEach((a) => {
    a.addEventListener("click", async (e) => {
      e.preventDefault();
      e.stopPropagation();
      const commentId = a.dataset.commentId;
      const bodyEl = container.querySelector(`.comment-body[data-comment-id="${commentId}"]`);
      if (!bodyEl) return;
      // Replace body with edit form, pre-filled with existing markdown
      const existingSource = bodyEl.dataset.markdownSource || "";
      bodyEl.innerHTML = `<form class="comment-edit-form" data-comment-id="${commentId}">
        <textarea name="markdownSource" rows="3" required></textarea>
        <button type="submit">Save</button>
        <button type="button" class="outline cancel-edit">Cancel</button>
      </form>`;
      bodyEl.querySelector("textarea").value = existingSource;
      const form = bodyEl.querySelector("form");
      form.querySelector(".cancel-edit").addEventListener("click", () => {
        // Reload to restore original - simple approach for reference UI
        window.dispatchEvent(new PopStateEvent("popstate"));
      });
      form.addEventListener("submit", async (ev) => {
        ev.preventDefault();
        const { put } = await import("../api.js");
        const fd = new FormData(form);
        try {
          await put(`/comments/${commentId}`, { markdownSource: fd.get("markdownSource") });
          window.dispatchEvent(new PopStateEvent("popstate"));
        } catch (err) {
          bodyEl.insertAdjacentHTML("beforeend", `<p><small style="color:var(--pico-del-color)">${escapeHtml(err.detail || "Failed to save")}</small></p>`);
        }
      });
    });
  });
}

function attachFormHandler(container, postSlug) {
  const form = container.querySelector("form");
  if (!form) return;
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const { post: apiPost } = await import("../api.js");
    const fd = new FormData(form);
    try {
      await apiPost(`/posts/${postSlug}/comments`, {
        parentCommentId: fd.get("parentCommentId") || null,
        markdownSource: fd.get("markdownSource"),
      });
      window.dispatchEvent(new PopStateEvent("popstate"));
    } catch (err) {
      container.insertAdjacentHTML("beforeend", `<p><small style="color:var(--pico-del-color)">${escapeHtml(err.detail || "Failed to post comment")}</small></p>`);
    }
  });
}

function escapeHtml(str) {
  if (!str) return "";
  const div = document.createElement("div");
  div.textContent = str;
  return div.innerHTML;
}

function escapeAttr(str) {
  if (!str) return "";
  return str.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}
