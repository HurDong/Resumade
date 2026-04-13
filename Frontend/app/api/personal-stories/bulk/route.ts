import { getRequiredServerBackendOrigin } from "@/lib/network/backend-origin"

export async function POST(request: Request) {
  const body = await request.text()

  const response = await fetch(`${getRequiredServerBackendOrigin()}/api/personal-stories/bulk`, {
    method: "POST",
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
