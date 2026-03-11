import { apiRequest } from "./client";
import {
  CommentDetail,
  CommentThreadResponse,
  CreateCommentRequest,
  EditCommentRequest,
} from "./types";

export function getComments(
  year: string,
  month: string,
  slug: string
): Promise<CommentThreadResponse> {
  return apiRequest<CommentThreadResponse>(
    `/posts/${year}/${month}/${slug}/comments`
  );
}

export function createComment(
  year: string,
  month: string,
  slug: string,
  data: CreateCommentRequest
): Promise<CommentDetail> {
  return apiRequest<CommentDetail>(
    `/posts/${year}/${month}/${slug}/comments`,
    {
      method: "POST",
      body: data,
      auth: true,
    }
  );
}

export function editComment(
  id: string,
  data: EditCommentRequest
): Promise<CommentDetail> {
  return apiRequest<CommentDetail>(`/comments/${id}`, {
    method: "PUT",
    body: data,
    auth: true,
  });
}
