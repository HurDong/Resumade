import { getRequiredServerBackendOrigin } from "@/lib/network/backend-origin";

export async function GET() {
  const response = await fetch(`${getRequiredServerBackendOrigin()}/api/profile-library`, {
    cache: "no-store",
  });

  const body = await response.text();

  return new Response(body, {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("content-type") ?? "application/json",
    },
  });
}

export async function PATCH(request: Request) {
  const body = await request.text();

  const response = await fetch(`${getRequiredServerBackendOrigin()}/api/profile-library/reorder`, {
    method: "PATCH",
    headers: {
      "Content-Type": request.headers.get("content-type") ?? "application/json",
    },
    body,
  });

  const responseBody = await response.text();

  return new Response(responseBody, {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("content-type") ?? "application/json",
    },
  });
}

export async function POST(request: Request) {
  const body = await request.text();

  const response = await fetch(`${getRequiredServerBackendOrigin()}/api/profile-library`, {
    method: "POST",
    headers: {
      "Content-Type": request.headers.get("content-type") ?? "application/json",
    },
    body,
  });

  const responseBody = await response.text();

  return new Response(responseBody, {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("content-type") ?? "application/json",
    },
  });
}
