export enum Role {
  GUEST = "GUEST",
  USER = "USER",
  ADMIN = "ADMIN",
  SUPER_ADMIN = "SUPER_ADMIN",
}

export interface UserPrincipal {
  id: string;
  username: string;
  displayName: string;
  role: Role;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  principal: UserPrincipal;
}

export interface RegistrationRequest {
  username: string;
  email: string;
  displayName: string;
  password: string;
}

export interface VerifyEmailRequest {
  token: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface TokenRefreshRequest {
  token: string;
}

export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}

export interface DeleteAccountRequest {
  username?: string;
}

export interface CommentNode {
  id: string;
  authorId: string | null;
  authorDisplayName: string;
  renderedHtml: string;
  createdAt: string;
  updatedAt: string;
  deleted: boolean;
  editable: boolean;
  children: CommentNode[];
}

export interface CommentThreadResponse {
  postId: string;
  comments: CommentNode[];
  totalActiveCount: number;
}

export interface CommentDetail {
  id: string;
  postId: string;
  authorDisplayName: string;
  renderedHtml: string;
  createdAt: string;
}

export interface CreateCommentRequest {
  parentCommentId?: string;
  markdownSource: string;
}

export interface EditCommentRequest {
  markdownSource: string;
}

export interface FactoidSummary {
  selector: string;
  locked: boolean;
  updatedBy: string | null;
  updatedAt: string;
}

export interface FactoidAttribute {
  type: string;
  value: string | null;
  rendered: string;
}

export interface FactoidDetail {
  selector: string;
  locked: boolean;
  updatedBy: string | null;
  updatedAt: string;
  attributes: FactoidAttribute[];
}

export interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface PostSummary {
  id: string;
  title: string;
  excerpt: string | null;
  authorDisplayName: string;
  publishedAt: string;
  slug: string;
}

export interface PostDetail {
  id: string;
  title: string;
  renderedHtml: string;
  excerpt: string | null;
  authorDisplayName: string;
  publishedAt: string;
  slug: string;
}

export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
}

export interface ContentSummary {
  id: string;
  title: string;
  slug: string;
  excerpt: string | null;
  authorDisplayName: string;
  publishedAt: string | null;
  createdAt: string;
}

export interface ContentListResponse {
  posts: ContentSummary[];
  page: number;
  totalPages: number;
  totalCount: number;
}

export interface ContentDetail {
  id: string;
  title: string;
  renderedHtml: string;
  excerpt: string | null;
  authorDisplayName: string;
  publishedAt: string;
  slug: string;
}

export interface ContentOperationConfirmation {
  id: string;
  message: string;
}

export interface ApprovePostRequest {
  publishedAt: string;
}

export interface CreateCategoryRequest {
  name: string;
  parentId?: string;
}

export interface CreateCategoryResponse {
  id: string;
  name: string;
  slug: string;
  parentName?: string;
}

export interface Category {
  id: string;
  name: string;
  slug: string;
  parentName?: string;
}

export interface UpdateUserRoleRequest {
  newRole: Role;
}

export interface PasswordResetResponse {
  username: string;
  temporaryPassword: string;
}
