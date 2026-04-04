import { getRequiredServerBackendOrigin } from "@/lib/network/backend-origin"

export async function GET() {
  const response = await fetch(`${getRequiredServerBackendOrigin()}/api/experiences`, {
    cache: "no-store",
  })

  const body = await response.text()

  return new Response(body, {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("content-type") ?? "application/json",
    },
  })
}
