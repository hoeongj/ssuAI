import { NextRequest, NextResponse } from "next/server";

const BACKEND_BASE = (
  process.env.SSUAI_API_PROXY_TARGET ||
  process.env.NEXT_PUBLIC_SSUAI_API_BASE ||
  "http://localhost:8080"
).replace(/\/$/, "");

export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);

  let backendRes: Response;
  try {
    backendRes = await fetch(
      `${BACKEND_BASE}/api/auth/saint/sso-callback?${searchParams.toString()}`,
      { redirect: "manual" },
    );
  } catch {
    return NextResponse.redirect(new URL("/auth/return?error=unknown", request.url));
  }

  // Success: backend returns 200 + Set-Cookie (PR #144 fix).
  // We re-set the cookie here so it lands on ssuai.vercel.app, not
  // ssumcp.duckdns.org — Vercel strips cross-domain cookies from rewrites.
  if (backendRes.status === 200) {
    const setCookie = backendRes.headers.get("set-cookie");
    const response = NextResponse.redirect(new URL("/auth/return?ok=1", request.url));
    if (setCookie) {
      response.headers.set("Set-Cookie", setCookie);
    }
    return response;
  }

  // Error paths: backend returns 302 with ?error=... query param.
  if (backendRes.status === 302 || backendRes.status === 301) {
    const location = backendRes.headers.get("location") ?? "";
    try {
      const search = new URL(location).search;
      return NextResponse.redirect(new URL(`/auth/return${search}`, request.url));
    } catch {
      // ignore malformed location
    }
  }

  return NextResponse.redirect(new URL("/auth/return?error=unknown", request.url));
}
