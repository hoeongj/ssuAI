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
  it("navigates to the backend sso-init endpoint on click", () => {
    render(<SaintLoginButton />);

    fireEvent.click(screen.getByRole("button", { name: /유세인트로 로그인/ }));

    expect(window.location.href).toBe(
      "http://localhost:8080/api/auth/saint/sso-init",
    );
  });

  it("trims a trailing slash off NEXT_PUBLIC_SSUAI_API_BASE before composing the URL", () => {
    process.env.NEXT_PUBLIC_SSUAI_API_BASE = "https://api.ssuai.test/";

    render(<SaintLoginButton />);
    fireEvent.click(screen.getByRole("button", { name: /유세인트로 로그인/ }));

    expect(window.location.href).toBe(
      "https://api.ssuai.test/api/auth/saint/sso-init",
    );
  });

  it("renders a custom label when supplied", () => {
    render(<SaintLoginButton label="로그인" />);

    expect(screen.getByRole("button", { name: "로그인" })).toBeInTheDocument();
  });
});
