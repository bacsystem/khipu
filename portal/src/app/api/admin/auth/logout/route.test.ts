import { describe, expect, it } from "vitest";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";
import { POST } from "./route";

describe("POST /api/admin/auth/logout", () => {
  it("limpia la cookie de sesión del administrador", async () => {
    const res = await POST();
    expect(res.cookies.get(COOKIE_ADMIN_ACCESS)?.value).toBe("");
  });
});
