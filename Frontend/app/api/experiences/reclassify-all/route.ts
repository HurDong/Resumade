const BACKEND_API_BASE_URL =
  process.env.RESUMADE_API_BASE_URL ?? "http://127.0.0.1:8080"

export async function POST() {
  const response = await fetch(`${BACKEND_API_BASE_URL}/api/experiences/reclassify-all`, {
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
