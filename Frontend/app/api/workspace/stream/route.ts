import { NextRequest } from "next/server";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const company = searchParams.get("company");
  const position = searchParams.get("position");
  const question = searchParams.get("question");

  if (!question || !company || !position) {
    return new Response("Company, position and question are required", { status: 400 });
  }

  const backendUrl = `${process.env.RESUMADE_API_BASE_URL || "http://127.0.0.1:8080"}/api/workspace/stream?company=${encodeURIComponent(company)}&position=${encodeURIComponent(position)}&question=${encodeURIComponent(question)}`;

  const response = await fetch(backendUrl, {
    headers: {
      "Accept": "text/event-stream",
    },
    cache: "no-store",
  });

  if (!response.ok) {
    return new Response("Failed to connect to backend SSE", { status: 500 });
  }

  // Create a TransformStream to pass-through the data
  const { readable, writable } = new TransformStream();
  response.body?.pipeTo(writable);

  return new Response(readable, {
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache, no-transform",
      "Connection": "keep-alive",
    },
  });
}
