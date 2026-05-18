import type { NextConfig } from "next";

const rawApiProxyTarget =
  process.env.SSUAI_API_PROXY_TARGET?.trim() ||
  process.env.NEXT_PUBLIC_SSUAI_API_BASE?.trim() ||
  "http://localhost:8080";
const apiProxyTarget = rawApiProxyTarget.replace(/\/$/, "");

const nextConfig: NextConfig = {
  async rewrites() {
    return {
      // afterFiles: Next.js checks its own API routes first, then falls
      // through to these rewrites. This lets app/api/auth/saint/sso-callback
      // handle the SmartID callback directly (so it can set the refresh cookie
      // on the Vercel domain) while all other /api/* calls are proxied.
      afterFiles: [
        {
          source: "/api/:path*",
          destination: `${apiProxyTarget}/api/:path*`,
        },
      ],
    };
  },
};

export default nextConfig;
