import { getRequiredServerBackendOrigin } from "@/lib/network/backend-origin"

export async function POST(request: Request) {
  const formData = await request.formData()

  const response = await fetch(`${getRequiredServerBackendOrigin()}/api/experiences/upload`, {
    method: "POST",
    body: formData,
  })

  const body = await response.text()

  return new Response(body, {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("content-type") ?? "application/json",
    },
  })
}
