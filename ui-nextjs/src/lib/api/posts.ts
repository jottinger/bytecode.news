import { PostDetail, PostSummary, SpringPage } from "./types";

/**
 * Stub: returns placeholder data until the backend post listing endpoint is ready.
 */
export async function listPosts(): Promise<SpringPage<PostSummary>> {
  return {
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: 20,
    first: true,
    last: true,
    empty: true,
  };
}

/**
 * Stub: returns placeholder data until the backend post detail endpoint is ready.
 */
export async function getPost(
  _year: string,
  _month: string,
  _slug: string
): Promise<PostDetail | null> {
  return null;
}
