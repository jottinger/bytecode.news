import { getFeatures } from "@/lib/api";
import { SubmitPostForm } from "@/components/submit-post-form";

export default async function SubmitPage() {
  let anonymousSubmission = false;

  try {
    const features = await getFeatures();
    anonymousSubmission = features.anonymousSubmission;
  } catch {
    // Keep strict default when feature service is unavailable.
  }

  return <SubmitPostForm anonymousSubmission={anonymousSubmission} />;
}
