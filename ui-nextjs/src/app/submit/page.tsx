import { getFeatures } from "@/lib/api";
import { SubmitPostForm } from "@/components/submit-post-form";

export default async function SubmitPage() {
  let anonymousSubmission = true;

  try {
    const features = await getFeatures();
    anonymousSubmission = features.anonymousSubmission;
  } catch {
    // Keep permissive fallback when feature service is unavailable.
    // Backend still enforces anonymous submission policy.
  }

  return <SubmitPostForm anonymousSubmission={anonymousSubmission} />;
}
