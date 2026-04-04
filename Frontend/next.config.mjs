const configuredBackendOrigin =
  process.env.RESUMADE_API_BASE_URL ??
  process.env.NEXT_PUBLIC_RESUMADE_API_BASE_URL

const normalizedBackendOrigin = configuredBackendOrigin?.replace(/\/$/, "")

/** @type {import('next').NextConfig} */
const nextConfig = {
  typescript: {
    ignoreBuildErrors: true,
  },
  images: {
    unoptimized: true,
  },
  async rewrites() {
    if (normalizedBackendOrigin) {
      return [
        {
          source: "/api/:path*",
          destination: `${normalizedBackendOrigin}/api/:path*`,
        },
      ]
    }

    if (process.env.NODE_ENV !== "production") {
      return [
        {
          source: "/api/:path*",
          destination: "http://127.0.0.1:8080/api/:path*",
        },
      ]
    }

    return []
  },
}

export default nextConfig
