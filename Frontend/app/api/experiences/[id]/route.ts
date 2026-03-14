const BACKEND_API_BASE_URL =
  process.env.RESUMADE_API_BASE_URL ?? "http://127.0.0.1:8080"

export async function DELETE(
  request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params

  const response = await fetch(`${BACKEND_API_BASE_URL}/api/experiences/${id}`, {
    method: "DELETE",
  })

  return new Response(null, {
    status: response.status,
  })
}
