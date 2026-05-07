import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "ssuAI",
  description: "Soongsil University student assistant dashboard",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
