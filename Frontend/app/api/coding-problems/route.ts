const BACKEND = process.env.RESUMADE_API_BASE_URL ?? "http://127.0.0.1:8080"
const BASE = `${BACKEND}/api/coding-problems`

export async function GET() {
  const res = await fetch(BASE, { cache: "no-store" })
  return new Response(await res.text(), {
    status: res.status,
    headers: { "Content-Type": res.headers.get("content-type") ?? "application/json" },
  })
}

export async function POST(request: Request) {
  const body = await request.text()
  const res = await fetch(BASE, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body,
  })
  return new Response(await res.text(), {
    status: res.status,
    headers: { "Content-Type": res.headers.get("content-type") ?? "application/json" },
  })
}
