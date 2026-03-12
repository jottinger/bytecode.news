export interface VersionInfo {
  name: string;
  version?: string;
  commit?: string;
  branch?: string;
  buildTime?: string;
}

export interface AuthenticationFeatures {
  otp: boolean;
  otpFrom?: string;
  oidc: {
    google: boolean;
    github: boolean;
  };
}

export interface FeaturesResponse {
  adapters: string[];
  ai: boolean;
  anonymousSubmission: boolean;
  authentication: AuthenticationFeatures;
  operationGroups: string[];
  siteName: string;
  version: VersionInfo;
}

export interface ContentSummary {
  id: string;
  title: string;
  slug: string;
  excerpt?: string;
  authorDisplayName: string;
  publishedAt?: string;
  categories: string[];
  tags: string[];
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
  slug: string;
  renderedHtml: string;
  excerpt?: string;
  authorDisplayName: string;
  publishedAt?: string;
  createdAt: string;
  updatedAt: string;
  commentCount: number;
  categories: string[];
  tags: string[];
}

export interface CommentNode {
  id: string;
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
