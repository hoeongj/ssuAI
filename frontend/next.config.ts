import type { NextConfig } from "next";

const rawApiProxyTarget =
  process.env.SSUAI_API_PROXY_TARGET?.trim() ||
  process.env.NEXT_PUBLIC_SSUAI_API_BASE?.trim() ||
  "http://localhost:8080";
const apiProxyTarget = rawApiProxyTarget.replace(/\/$/, "");

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${apiProxyTarget}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
