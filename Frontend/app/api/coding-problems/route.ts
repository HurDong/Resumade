import { getRequiredServerBackendOrigin } from "@/lib/network/backend-origin"

function getBaseUrl() {
  return `${getRequiredServerBackendOrigin()}/api/coding-problems`
}

export async function GET() {
  const res = await fetch(getBaseUrl(), { cache: "no-store" })
  return new Response(await res.text(), {
    status: res.status,
    headers: { "Content-Type": res.headers.get("content-type") ?? "application/json" },
  })
}

export async function POST(request: Request) {
  const body = await request.text()
  const res = await fetch(getBaseUrl(), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body,
  })
  return new Response(await res.text(), {
    status: res.status,
    headers: { "Content-Type": res.headers.get("content-type") ?? "application/json" },
  })
}
