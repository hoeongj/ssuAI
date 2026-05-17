"use client";

import { useState } from "react";

import { Library, LogIn } from "lucide-react";

import { LibraryLoginModal } from "@/components/library/LibraryLoginModal";
import { Button } from "@/components/ui/button";
import { useLibraryAuth } from "@/contexts/LibraryAuthContext";
import { useSaintAuth } from "@/hooks/useSaintAuth";
import { getSsoInitUrl } from "@/lib/api/auth";

export function HeaderAuthStatus() {
  const { isAuthenticated, isLoading } = useSaintAuth();
  const { isConnected: libraryConnected } = useLibraryAuth();
  const [showLibraryModal, setShowLibraryModal] = useState(false);

  if (isLoading) return null;

  return (
    <>
      <div className="flex items-center gap-1.5">
        {!isAuthenticated && (
          <Button
            variant="outline"
            size="sm"
            className="h-7 gap-1 px-2 text-xs"
            onClick={() => { window.location.href = getSsoInitUrl(); }}
          >
            <LogIn className="h-3 w-3" aria-hidden="true" />
            <span className="hidden sm:inline">SmartID 로그인</span>
            <span className="sm:hidden">로그인</span>
          </Button>
        )}

        {libraryConnected ? (
          <span className="flex items-center gap-1 rounded-md border border-emerald-200 bg-emerald-50 px-2 py-1 text-xs font-medium text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950 dark:text-emerald-400">
            <Library className="h-3 w-3" aria-hidden="true" />
            <span className="hidden sm:inline">도서관 연결됨</span>
            <span className="sm:hidden">도서관</span>
          </span>
        ) : (
          <Button
            variant="outline"
            size="sm"
            className="h-7 gap-1 px-2 text-xs"
            onClick={() => setShowLibraryModal(true)}
          >
            <Library className="h-3 w-3" aria-hidden="true" />
            <span className="hidden sm:inline">도서관 연동</span>
            <span className="sm:hidden">도서관</span>
          </Button>
        )}
      </div>

      {showLibraryModal && (
        <LibraryLoginModal onClose={() => setShowLibraryModal(false)} />
      )}
    </>
  );
}
