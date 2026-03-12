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

export interface CategorySummary {
  id: string;
  name: string;
  parentName?: string | null;
}

export interface ContentSummary {
  id: string;
  title: string;
  slug: string;
  excerpt?: string;
  authorDisplayName: string;
  publishedAt?: string;
  commentCount: number;
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
  status?: string;
  publishedAt?: string;
  createdAt: string;
  updatedAt: string;
  commentCount: number;
  categories: string[];
  tags: string[];
  markdownSource?: string | null;
}

export interface CommentNode {
  id: string;
  authorDisplayName: string;
  renderedHtml: string;
  markdownSource?: string | null;
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

export interface FactoidSummary {
  selector: string;
  locked: boolean;
  updatedBy?: string | null;
  updatedAt: string;
  lastAccessedAt?: string | null;
  accessCount: number;
}

export interface FactoidListResponse {
  factoids: FactoidSummary[];
  page: number;
  totalPages: number;
  totalCount: number;
}

export interface FactoidAttribute {
  type: string;
  value?: string | null;
  rendered?: string | null;
}

export interface FactoidDetailResponse {
  selector: string;
  locked: boolean;
  updatedBy?: string | null;
  updatedAt: string;
  lastAccessedAt?: string | null;
  accessCount: number;
  attributes: FactoidAttribute[];
}

export interface KarmaLeaderboardEntry {
  subject: string;
  score: number;
  lastUpdated: string;
}

export interface KarmaLeaderboardResponse {
  top: KarmaLeaderboardEntry[];
  bottom: KarmaLeaderboardEntry[];
  limit: number;
}

export interface LogProvenanceSummary {
  provenanceUri: string;
  protocol: string;
  serviceId?: string | null;
  replyTo: string;
  latestTimestamp?: string | null;
  latestSender?: string | null;
  latestContentPreview?: string | null;
}

export interface LogProvenanceListResponse {
  provenances: LogProvenanceSummary[];
}

export interface LogEntry {
  timestamp: string;
  sender: string;
  content: string;
  direction: string;
}

export interface LogDayResponse {
  provenanceUri: string;
  day: string;
  entries: LogEntry[];
}
