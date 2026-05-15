import type { Metadata, Viewport } from "next";
import { Providers } from "./providers";
import "./globals.css";

export const metadata: Metadata = {
  title: "ssuAI",
  description: "Soongsil University student assistant dashboard",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  // Pinch-to-zoom stays enabled (no maximumScale / userScalable lock) so the
  // page stays accessible. The auto-zoom-on-input behavior on iOS Safari is
  // killed by sizing every input ≥ 16px on mobile, not by disabling zoom.
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
