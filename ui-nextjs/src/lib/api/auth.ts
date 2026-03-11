import { apiRequest } from "./client";
import {
  ChangePasswordRequest,
  DeleteAccountRequest,
  ForgotPasswordRequest,
  LoginRequest,
  LoginResponse,
  RegistrationRequest,
  ResetPasswordRequest,
  TokenRefreshRequest,
  UserPrincipal,
  VerifyEmailRequest,
} from "./types";

export function login(data: LoginRequest): Promise<LoginResponse> {
  return apiRequest<LoginResponse>("/auth/login", {
    method: "POST",
    body: data,
  });
}

export function register(data: RegistrationRequest): Promise<UserPrincipal> {
  return apiRequest<UserPrincipal>("/auth/register", {
    method: "POST",
    body: data,
  });
}

export function verifyEmail(data: VerifyEmailRequest): Promise<string> {
  return apiRequest<string>("/auth/verify", {
    method: "POST",
    body: data,
  });
}

export function logout(): Promise<void> {
  return apiRequest<void>("/auth/logout", { method: "POST" });
}

export function forgotPassword(data: ForgotPasswordRequest): Promise<string> {
  return apiRequest<string>("/auth/forgot-password", {
    method: "POST",
    body: data,
  });
}

export function resetPassword(data: ResetPasswordRequest): Promise<string> {
  return apiRequest<string>("/auth/reset-password", {
    method: "POST",
    body: data,
  });
}

export function refreshToken(data: TokenRefreshRequest): Promise<LoginResponse> {
  return apiRequest<LoginResponse>("/auth/refresh", {
    method: "POST",
    body: data,
  });
}

export function changePassword(data: ChangePasswordRequest): Promise<string> {
  return apiRequest<string>("/auth/password", {
    method: "PUT",
    body: data,
    auth: true,
  });
}

export function deleteAccount(data: DeleteAccountRequest): Promise<string> {
  return apiRequest<string>("/auth/account", {
    method: "DELETE",
    body: data,
    auth: true,
  });
}
