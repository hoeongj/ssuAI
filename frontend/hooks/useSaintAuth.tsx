"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import type { ReactNode } from "react";

import { fetchMe, refreshAccessToken, type AuthMe } from "@/lib/api/auth";
import { ApiError } from "@/lib/api/types";

export interface SaintAuthState {
  user: AuthMe | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  /** Force a refresh-cookie → access-JWT → /me cycle. Returns true on success. */
  refresh: () => Promise<boolean>;
  /** Clear in-memory auth state. Does NOT invalidate the server-side refresh cookie. */
  logout: () => void;
}

const SaintAuthContext = createContext<SaintAuthState | null>(null);

export function SaintAuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthMe | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const hasHydrated = useRef(false);

  const refresh = useCallback(async (): Promise<boolean> => {
    setIsLoading(true);
    try {
      const { accessToken: newAccess } = await refreshAccessToken();
      const me = await fetchMe(newAccess);
      setAccessToken(newAccess);
      setUser(me);
      return true;
    } catch (error) {
      // 401 = anonymous visitor with no refresh cookie. Silent.
      if (!(error instanceof ApiError && error.httpStatus === 401)) {
        console.warn("ssuAI auth refresh failed", error);
      }
      setAccessToken(null);
      setUser(null);
      return false;
    } finally {
      setIsLoading(false);
    }
  }, []);

  const logout = useCallback(() => {
    setAccessToken(null);
    setUser(null);
  }, []);

  // Try to hydrate on first mount. If the user has a valid refresh cookie
  // from a previous SSO flow, they will appear logged in without clicking
  // the SSO button again.
  useEffect(() => {
    if (hasHydrated.current) {
      return;
    }
    hasHydrated.current = true;
    void refresh();
  }, [refresh]);

  const value: SaintAuthState = {
    user,
    isLoading,
    isAuthenticated: !!accessToken && !!user,
    refresh,
    logout,
  };

  return <SaintAuthContext.Provider value={value}>{children}</SaintAuthContext.Provider>;
}

export function useSaintAuth(): SaintAuthState {
  const ctx = useContext(SaintAuthContext);
  if (!ctx) {
    throw new Error("useSaintAuth must be used within <SaintAuthProvider>");
  }
  return ctx;
}
