"use client"

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string }
  reset: () => void
}) {
  return (
    <html lang="ko">
      <body className="min-h-screen bg-[radial-gradient(circle_at_top,_rgba(15,23,42,0.08),_transparent_42%),linear-gradient(180deg,_#fcfcfd_0%,_#f6f7fb_100%)] text-slate-900">
        <main className="flex min-h-screen items-center justify-center p-6">
          <section className="w-full max-w-xl rounded-[32px] border border-slate-200/80 bg-white/90 p-8 shadow-[0_24px_80px_-32px_rgba(15,23,42,0.35)] backdrop-blur">
            <div className="inline-flex rounded-full border border-amber-200 bg-amber-50 px-3 py-1 text-xs font-semibold tracking-[0.18em] text-amber-700">
              RESUMADE WORKSPACE
            </div>
            <h1 className="mt-5 text-3xl font-semibold tracking-tight text-slate-950">
              작업 화면을 다시 불러와야 합니다.
            </h1>
            <p className="mt-3 text-sm leading-6 text-slate-600">
              예상하지 못한 오류로 현재 화면 상태를 유지하지 못했습니다. 아래 버튼으로
              워크스페이스를 다시 렌더링할 수 있습니다.
            </p>

            <div className="mt-6 rounded-2xl border border-slate-200 bg-slate-50/80 p-4 text-sm text-slate-700">
              <p className="font-medium text-slate-900">오류 메시지</p>
              <p className="mt-2 break-words leading-6">{error.message}</p>
            </div>

            <div className="mt-6 flex flex-wrap items-center gap-3">
              <button
                onClick={() => reset()}
                className="inline-flex h-11 items-center justify-center rounded-full bg-slate-950 px-5 text-sm font-semibold text-white transition hover:bg-slate-800"
              >
                다시 시도
              </button>
              {error.digest ? (
                <span className="text-xs font-medium tracking-wide text-slate-500">
                  Error digest: {error.digest}
                </span>
              ) : null}
            </div>
          </section>
        </main>
      </body>
    </html>
  )
}
