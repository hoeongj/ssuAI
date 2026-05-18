import { NextRequest, NextResponse } from "next/server";

const BACKEND_BASE = (
  process.env.SSUAI_API_PROXY_TARGET ||
  process.env.NEXT_PUBLIC_SSUAI_API_BASE ||
  "http://localhost:8080"
).replace(/\/$/, "");

export async function proxy(request: NextRequest) {
  const { searchParams } = request.nextUrl;

  let backendRes: Response;
  try {
    backendRes = await fetch(
      `${BACKEND_BASE}/api/auth/saint/sso-callback?${searchParams.toString()}`,
      { redirect: "manual" },
    );
  } catch {
    return NextResponse.redirect(new URL("/auth/return?error=unknown", request.url));
  }

  const setCookieHeader = backendRes.headers.get("set-cookie");

  if (backendRes.status === 200 && setCookieHeader) {
    // Parse Set-Cookie: <name>=<value>; Path=...; Max-Age=...; ...
    // JWT values contain "=" so we split only on the first "=".
    const parts = setCookieHeader.split(";");
    const firstEq = parts[0].indexOf("=");
    const cookieName = parts[0].substring(0, firstEq).trim();
    const cookieValue = parts[0].substring(firstEq + 1).trim();

    const maxAgePart = parts.find((p) =>
      p.trim().toLowerCase().startsWith("max-age="),
    );
    const maxAge = maxAgePart ? parseInt(maxAgePart.split("=")[1]!, 10) : 1209600;

    const response = NextResponse.redirect(new URL("/auth/return?ok=1", request.url));
    // Use cookies.set() — Next.js proxy strips manually-set Set-Cookie headers.
    response.cookies.set({
      name: cookieName,
      value: cookieValue,
      httpOnly: true,
      secure: true,
      sameSite: "none",
      path: "/api/auth",
      maxAge,
    });
    return response;
  }

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

export const config = {
  matcher: ["/api/auth/saint/sso-callback"],
};
