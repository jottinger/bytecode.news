"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { verifyEmail } from "@/lib/api/auth";
import { ApiError } from "@/lib/api/client";
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";

function VerifyContent() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token");
  const [status, setStatus] = useState<"loading" | "success" | "error">(
    "loading"
  );
  const [message, setMessage] = useState("");

  useEffect(() => {
    if (!token) {
      setStatus("error");
      setMessage("No verification token provided.");
      return;
    }

    verifyEmail({ token })
      .then(() => {
        setStatus("success");
        setMessage("Your email has been verified. You can now log in.");
      })
      .catch((error) => {
        setStatus("error");
        setMessage(
          error instanceof ApiError
            ? error.detail
            : "Verification failed. The link may have expired."
        );
      });
  }, [token]);

  return (
    <Card>
      <CardHeader className="text-center">
        <CardTitle className="text-2xl">Email Verification</CardTitle>
      </CardHeader>
      <CardContent className="text-center">
        {status === "loading" && (
          <p className="text-muted-foreground">Verifying your email...</p>
        )}
        {status === "success" && <p className="text-green-600">{message}</p>}
        {status === "error" && <p className="text-destructive">{message}</p>}
      </CardContent>
      {status !== "loading" && (
        <CardFooter className="justify-center">
          <Button asChild>
            <Link href="/login">Go to login</Link>
          </Button>
        </CardFooter>
      )}
    </Card>
  );
}

export default function VerifyPage() {
  return (
    <Suspense>
      <VerifyContent />
    </Suspense>
  );
}
