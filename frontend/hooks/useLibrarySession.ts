"use client";

import { useState } from "react";

import { useQueryClient } from "@tanstack/react-query";

import { captureLibrarySession } from "@/lib/api/library";

export type LibrarySessionState = "idle" | "submitting" | "success" | "error";

export function useLibrarySession() {
  const queryClient = useQueryClient();
  const [state, setState] = useState<LibrarySessionState>("idle");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  async function submitToken(token: string): Promise<boolean> {
    setState("submitting");
    setErrorMessage(null);
    try {
      await captureLibrarySession(token.trim());
      setState("success");
      await queryClient.invalidateQueries({ queryKey: ["library", "seats"] });
      return true;
    } catch {
      setState("error");
      setErrorMessage("토큰 저장에 실패했습니다. 값을 다시 확인해주세요.");
      return false;
    }
  }

  function reset() {
    setState("idle");
    setErrorMessage(null);
  }

  return { state, errorMessage, submitToken, reset };
}
