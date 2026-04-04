import { getRequiredServerBackendOrigin } from "@/lib/network/backend-origin"

export async function PUT(request: Request) {
  const body = await request.text()
  const res = await fetch(`${getRequiredServerBackendOrigin()}/api/tech-notes/reorder`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body,
  })

  return new Response(await res.text(), {
    status: res.status,
    headers: { "Content-Type": res.headers.get("content-type") ?? "application/json" },
  })
}
