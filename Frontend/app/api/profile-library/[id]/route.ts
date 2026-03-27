const BACKEND_API_BASE_URL =
  process.env.RESUMADE_API_BASE_URL ?? "http://127.0.0.1:8080";

export async function PUT(
  request: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;
  const body = await request.text();

  const response = await fetch(`${BACKEND_API_BASE_URL}/api/profile-library/${id}`, {
    method: "PUT",
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

export async function DELETE(
  request: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;

  const response = await fetch(`${BACKEND_API_BASE_URL}/api/profile-library/${id}`, {
    method: "DELETE",
  });

  const responseBody = await response.text();

  return new Response(responseBody, {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("content-type") ?? "text/plain; charset=utf-8",
    },
  });
}
