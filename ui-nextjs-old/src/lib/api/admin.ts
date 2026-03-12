import { apiRequest } from "./client";
import {
  ApprovePostRequest,
  Category,
  ContentDetail,
  ContentListResponse,
  ContentOperationConfirmation,
  CreateCategoryRequest,
  CreateCategoryResponse,
  PasswordResetResponse,
  UpdateUserRoleRequest,
} from "./types";

export function getPendingPosts(
  page: number,
  size: number,
  deleted: boolean = false
): Promise<ContentListResponse> {
  return apiRequest<ContentListResponse>(
    `/admin/posts/pending?page=${page}&size=${size}&deleted=${deleted}`,
    { auth: true }
  );
}

export function approvePost(
  id: string,
  data: ApprovePostRequest
): Promise<ContentDetail> {
  return apiRequest<ContentDetail>(`/admin/posts/${id}/approve`, {
    method: "PUT",
    body: data,
    auth: true,
  });
}

export function deletePost(
  id: string,
  hard: boolean = false
): Promise<ContentOperationConfirmation> {
  return apiRequest<ContentOperationConfirmation>(
    `/admin/posts/${id}?hard=${hard}`,
    { method: "DELETE", auth: true }
  );
}

export function deleteComment(
  id: string,
  hard: boolean = false
): Promise<ContentOperationConfirmation> {
  return apiRequest<ContentOperationConfirmation>(
    `/admin/comments/${id}?hard=${hard}`,
    { method: "DELETE", auth: true }
  );
}

export function createCategory(
  data: CreateCategoryRequest
): Promise<CreateCategoryResponse> {
  return apiRequest<CreateCategoryResponse>("/admin/categories", {
    method: "POST",
    body: data,
    auth: true,
  });
}

export function deleteCategory(
  id: string
): Promise<ContentOperationConfirmation> {
  return apiRequest<ContentOperationConfirmation>(
    `/admin/categories/${id}`,
    { method: "DELETE", auth: true }
  );
}

export function getCategories(): Promise<Category[]> {
  return apiRequest<Category[]>("/categories");
}

export function updateUserRole(
  username: string,
  data: UpdateUserRoleRequest
): Promise<void> {
  return apiRequest<void>(`/admin/users/${username}/role`, {
    method: "PUT",
    body: data,
    auth: true,
  });
}

export function resetUserPassword(
  username: string
): Promise<PasswordResetResponse> {
  return apiRequest<PasswordResetResponse>(
    `/admin/users/${username}/reset-password`,
    { method: "POST", auth: true }
  );
}
