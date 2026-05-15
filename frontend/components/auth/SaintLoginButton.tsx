"use client";

import { LogIn } from "lucide-react";

import { Button } from "@/components/ui/button";
import { getSsoInitUrl } from "@/lib/api/auth";

interface SaintLoginButtonProps {
  label?: string;
  className?: string;
}

/**
 * Single click → full-page navigation to the backend's SmartID SSO entry.
 * The backend redirects to SmartID with our callback URL baked in, so the
 * frontend never has to know SmartID's URL or query params (ADR 0014).
 */
export function SaintLoginButton({
  label = "유세인트로 로그인",
  className,
}: SaintLoginButtonProps) {
  function handleClick() {
    window.location.href = getSsoInitUrl();
  }

  return (
    <Button onClick={handleClick} className={className}>
      <LogIn className="h-4 w-4" aria-hidden="true" />
      {label}
    </Button>
  );
}
