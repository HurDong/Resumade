import { getRequiredServerBackendOrigin } from "@/lib/network/backend-origin"

export async function PUT(
  request: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params
  const body = await request.text()
  const res = await fetch(`${getRequiredServerBackendOrigin()}/api/coding-problems/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body,
  })
  return new Response(await res.text(), {
    status: res.status,
    headers: { "Content-Type": res.headers.get("content-type") ?? "application/json" },
  })
}

export async function DELETE(
  _request: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params
  const res = await fetch(`${getRequiredServerBackendOrigin()}/api/coding-problems/${id}`, {
    method: "DELETE",
  })
  return new Response(await res.text(), {
    status: res.status,
    headers: { "Content-Type": res.headers.get("content-type") ?? "text/plain" },
  })
}
