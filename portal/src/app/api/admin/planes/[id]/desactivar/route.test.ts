import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";

vi.mock("@/lib/api/admin-planes", () => ({ desactivarPlan: vi.fn() }));

import { desactivarPlan } from "@/lib/api/admin-planes";
import { POST } from "./route";

const ID = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";

afterEach(() => vi.mocked(desactivarPlan).mockReset());

/** BFF de «desactivar un plan» (#190): la validación común está en `comun.test.ts`; acá, que esta ruta llama a la acción correcta. */
describe("POST /api/admin/planes/[id]/desactivar", () => {
  it("desactiva el plan con el JWT del administrador", async () => {
    vi.mocked(desactivarPlan).mockResolvedValue({ id: ID, estado: "INACTIVO" } as never);

    const res = await POST(
      new NextRequest(`http://localhost/api/admin/planes/${ID}/desactivar`, { method: "POST", headers: { cookie: `${COOKIE_ADMIN_ACCESS}=jwt-admin` } }),
      { params: Promise.resolve({ id: ID }) },
    );

    expect(res.status).toBe(200);
    expect((await res.json()).datos).toEqual({ id: ID, estado: "INACTIVO" });
    expect(desactivarPlan).toHaveBeenCalledWith("jwt-admin", ID, {});
  });
});
