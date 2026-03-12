import { EditPostForm } from "@/components/edit-post-form";

export default async function EditPostPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <EditPostForm postId={id} />;
}
