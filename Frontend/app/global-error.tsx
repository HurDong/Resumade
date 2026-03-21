'use client';

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <html lang="en">
      <body>
        <div style={{ padding: '20px', fontFamily: 'sans-serif' }}>
          <h2>Application Error</h2>
          <p>{error.message}</p>
          <button onClick={() => reset()} style={{ padding: '8px 16px', cursor: 'pointer' }}>Try again</button>
        </div>
      </body>
    </html>
  );
}
