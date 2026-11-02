import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api/auth", () => ({
  recuperar: vi.fn(),
}));

import { recuperar } from "@/lib/api/auth";
import { POST } from "./route";

function postRequest(body: unknown, extra: Record<string, string> = {}) {
  return new NextRequest("http://localhost/api/auth/recuperar", {
    method: "POST",
    body: JSON.stringify(body),
    headers: { "content-type": "application/json", ...extra },
  });
}

describe("POST /api/auth/recuperar", () => {
  afterEach(() => vi.unstubAllEnvs());

  it("responde 202 siempre, sin decir si el correo existe", async () => {
    vi.mocked(recuperar).mockResolvedValue(undefined);

    const res = await POST(postRequest({ email: "a@b.com" }));

    expect(res.status).toBe(202);
  });

  /** Con la IP del cliente el backend la registra igual que en el login (#261); la del navegador nunca cruza. */
  it("manda al backend la IP de confianza ya resuelta", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(recuperar).mockResolvedValue(undefined);

    await POST(postRequest({ email: "a@b.com" }, { "x-forwarded-for": "6.6.6.6, 203.0.113.7" }));

    expect(recuperar).toHaveBeenCalledWith("a@b.com", { "X-Forwarded-For": "203.0.113.7" });
  });
});
