import { NextRequest } from "next/server";
import { describe, expect, it } from "vitest";
import { COOKIE_REFRESH } from "@/lib/session";
import { middleware } from "./middleware";

describe("middleware", () => {
  it("redirige a /login cuando no hay cookie de sesión", () => {
    const req = new NextRequest("http://localhost/comprobantes");

    const res = middleware(req);

    expect(res.status).toBe(307);
    const location = new URL(res.headers.get("location")!);
    expect(location.pathname).toBe("/login");
    expect(location.searchParams.get("next")).toBe("/comprobantes");
  });

  it("deja pasar cuando hay refresh", () => {
    const req = new NextRequest("http://localhost/comprobantes", {
      headers: { cookie: `${COOKIE_REFRESH}=r1` },
    });

    const res = middleware(req);

    expect(res.status).toBe(200);
    expect(res.headers.get("location")).toBeNull();
  });
});
