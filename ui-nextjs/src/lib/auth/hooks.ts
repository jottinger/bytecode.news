"use client";

import { useContext, useEffect } from "react";
import { useRouter } from "next/navigation";
import { AuthContext } from "./context";
import { Role } from "@/lib/api/types";

export function useAuth() {
  return useContext(AuthContext);
}

export function useRequireAuth() {
  const auth = useContext(AuthContext);
  const router = useRouter();

  useEffect(() => {
    if (!auth.isLoading && !auth.isAuthenticated) {
      router.push("/login");
    }
  }, [auth.isLoading, auth.isAuthenticated, router]);

  return auth;
}

export function useRequireAdmin() {
  const auth = useContext(AuthContext);
  const router = useRouter();

  useEffect(() => {
    if (!auth.isLoading) {
      if (!auth.isAuthenticated) {
        router.push("/login");
      } else if (
        auth.user?.role !== Role.ADMIN &&
        auth.user?.role !== Role.SUPER_ADMIN
      ) {
        router.push("/");
      }
    }
  }, [auth.isLoading, auth.isAuthenticated, auth.user?.role, router]);

  return auth;
}
