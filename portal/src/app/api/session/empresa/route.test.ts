import { NextRequest } from "next/server";
import { describe, expect, it } from "vitest";
import { COOKIE_EMPRESA } from "@/lib/session";
import { POST } from "./route";

describe("POST /api/session/empresa", () => {
  it("fija la cookie de empresa activa", async () => {
    const req = new NextRequest("http://localhost/api/session/empresa", {
      method: "POST",
      body: JSON.stringify({ empresaId: "e1" }),
      headers: { "content-type": "application/json" },
    });

    const res = await POST(req);

    expect(res.cookies.get(COOKIE_EMPRESA)?.value).toBe("e1");
  });
});
