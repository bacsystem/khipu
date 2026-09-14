import { NextRequest } from "next/server";
import { describe, expect, it, vi } from "vitest";
import { COOKIE_ACCESS, COOKIE_REFRESH } from "@/lib/session";

vi.mock("@/lib/api/auth", () => ({
  logout: vi.fn().mockResolvedValue(undefined),
}));

import { logout } from "@/lib/api/auth";
import { POST } from "./route";

function requestWithSession() {
  return new NextRequest("http://localhost/api/auth/logout", {
    method: "POST",
    headers: { cookie: `${COOKIE_ACCESS}=a1; ${COOKIE_REFRESH}=r1` },
  });
}

describe("POST /api/auth/logout", () => {
  it("limpia las cookies de sesión y avisa al backend", async () => {
    const res = await POST(requestWithSession());

    expect(res.cookies.get(COOKIE_ACCESS)?.value).toBe("");
    expect(res.cookies.get(COOKIE_REFRESH)?.value).toBe("");
    expect(logout).toHaveBeenCalledWith("a1", "r1");
  });

  it("limpia las cookies aunque el backend falle", async () => {
    vi.mocked(logout).mockRejectedValueOnce(new Error("caído"));

    const res = await POST(requestWithSession());

    expect(res.cookies.get(COOKIE_ACCESS)?.value).toBe("");
  });
});
