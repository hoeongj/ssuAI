import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SaintLoginButton } from "./SaintLoginButton";

const originalApiBase = process.env.NEXT_PUBLIC_SSUAI_API_BASE;
const originalLocation = window.location;

beforeEach(() => {
  process.env.NEXT_PUBLIC_SSUAI_API_BASE = "http://localhost:8080";
  // jsdom 's window.location is not assignable; substitute with a writable spy.
  Object.defineProperty(window, "location", {
    configurable: true,
    writable: true,
    value: { href: "" } as Location,
  });
});

afterEach(() => {
  Object.defineProperty(window, "location", {
    configurable: true,
    writable: true,
    value: originalLocation,
  });
  process.env.NEXT_PUBLIC_SSUAI_API_BASE = originalApiBase;
  vi.restoreAllMocks();
});

describe("SaintLoginButton", () => {
  it("navigates to the same-origin sso-init endpoint on click", () => {
    render(<SaintLoginButton />);

    fireEvent.click(screen.getByRole("button", { name: /유세인트로 로그인/ }));

    expect(window.location.href).toBe("/api/auth/saint/sso-init");
  });

  it("keeps browser navigation same-origin even when an API base env exists", () => {
    process.env.NEXT_PUBLIC_SSUAI_API_BASE = "https://api.ssuai.test/";

    render(<SaintLoginButton />);
    fireEvent.click(screen.getByRole("button", { name: /유세인트로 로그인/ }));

    expect(window.location.href).toBe("/api/auth/saint/sso-init");
  });

  it("renders a custom label when supplied", () => {
    render(<SaintLoginButton label="로그인" />);

    expect(screen.getByRole("button", { name: "로그인" })).toBeInTheDocument();
  });
});
