import { getBackendBaseUrl } from "@/lib/backend-url";

export async function DELETE(
  request: Request,
  context: { params: Promise<{ id: string }> },
) {
  const { id } = await context.params;
  const url = new URL(request.url);
  const hard = url.searchParams.get("hard") || "false";
  const auth = request.headers.get("authorization");

  const response = await fetch(
    `${getBackendBaseUrl()}/admin/posts/${id}?hard=${encodeURIComponent(hard)}`,
    {
      method: "DELETE",
      headers: {
        Accept: "application/json",
        ...(auth ? { Authorization: auth } : {}),
      },
      cache: "no-store",
    },
  );

  return new Response(await response.text(), {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("content-type") || "application/json",
    },
  });
}
