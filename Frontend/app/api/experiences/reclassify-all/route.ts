import { getRequiredServerBackendOrigin } from "@/lib/network/backend-origin"

export async function POST() {
  const response = await fetch(`${getRequiredServerBackendOrigin()}/api/experiences/reclassify-all`, {
    method: "POST",
  })

  const body = await response.text()

  return new Response(body, {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("content-type") ?? "application/json",
    },
  })
}
