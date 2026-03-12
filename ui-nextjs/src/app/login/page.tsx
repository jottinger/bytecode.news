import { getFeatures } from "@/lib/api";
import { OtpLoginForm } from "@/components/otp-login-form";

export default async function LoginPage({
  searchParams,
}: {
  searchParams?: Promise<{ return?: string }>;
}) {
  const params = (await searchParams) || {};
  const returnPath = params.return || "/";

  let otpEnabled = true;
  let otpFrom = "noreply@bytecode.news";
  let googleEnabled = false;
  let githubEnabled = false;

  try {
    const features = await getFeatures();
    otpEnabled = features.authentication?.otp !== false;
    otpFrom = features.authentication?.otpFrom || otpFrom;
    googleEnabled = features.authentication?.oidc?.google === true;
    githubEnabled = features.authentication?.oidc?.github === true;
  } catch {
    // If features are unavailable, default to OTP enabled and OIDC hidden.
  }

  return (
    <OtpLoginForm
      returnPath={returnPath}
      otpEnabled={otpEnabled}
      otpFrom={otpFrom}
      googleEnabled={googleEnabled}
      githubEnabled={githubEnabled}
    />
  );
}
