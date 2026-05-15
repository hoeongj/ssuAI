import { fetchJson } from "./client";

export interface AuthMe {
  studentId: string;
  name: string;
  major: string | null;
  enrollmentStatus: string | null;
}

export interface RefreshResponse {
  accessToken: string;
  accessTtlSeconds: number;
}

export async function refreshAccessToken(): Promise<RefreshResponse> {
  return fetchJson<RefreshResponse>("/api/auth/refresh", {
    method: "POST",
    credentials: "include",
  });
}

export async function fetchMe(accessToken: string): Promise<AuthMe> {
  return fetchJson<AuthMe>("/api/auth/me", {
    method: "GET",
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });
}

/**
 * Build the absolute URL the browser should navigate to in order to
 * kick off the SmartID SSO flow. The backend's `/sso-init` endpoint
 * 302s to SmartID with `apiReturnUrl` already baked in, so the
 * frontend never has to know the SmartID URL or compose query params.
 */
export function getSsoInitUrl(): string {
  const base = process.env.NEXT_PUBLIC_SSUAI_API_BASE;
  if (!base) {
    throw new Error("NEXT_PUBLIC_SSUAI_API_BASE is not set");
  }
  return `${base.replace(/\/$/, "")}/api/auth/saint/sso-init`;
}
