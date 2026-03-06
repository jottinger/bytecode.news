/** Render a new comment or reply form */
export function renderCommentForm(postSlug, parentCommentId) {
  const parentInput = parentCommentId
    ? `<input type="hidden" name="parentCommentId" value="${parentCommentId}" />`
    : "";

  return `<form class="comment-new-form">
    ${parentInput}
    <textarea name="markdownSource" rows="3" placeholder="Write a comment..."></textarea>
    <button type="submit">Post Comment</button>
  </form>`;
}
