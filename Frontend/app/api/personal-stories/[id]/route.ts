import { getRequiredServerBackendOrigin } from "@/lib/network/backend-origin"

export async function PUT(
  request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params
  const body = await request.text()

  const response = await fetch(`${getRequiredServerBackendOrigin()}/api/personal-stories/${id}`, {
    method: "PUT",
    headers: {
      "Content-Type": request.headers.get("content-type") ?? "application/json",
    },
    body,
  })

  const responseBody = await response.text()

  return new Response(responseBody, {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("content-type") ?? "application/json",
    },
  })
}

export async function DELETE(
  request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params

  const response = await fetch(`${getRequiredServerBackendOrigin()}/api/personal-stories/${id}`, {
    method: "DELETE",
  })

  return new Response(null, {
    status: response.status,
  })
}
