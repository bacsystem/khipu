import { describe, expect, it } from "vitest";
import { esUuid } from "./uuid";

describe("esUuid", () => {
  it("acepta un UUID en cualquier caja", () => {
    expect(esUuid("0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11")).toBe(true);
    expect(esUuid("0B1F1C3E-0F1C-4B53-9A1E-2F6F6D0C7A11")).toBe(true);
  });

  it("rechaza todo lo demás: rutas, espacios, sufijos, guiones de menos", () => {
    for (const malo of ["", "nueva", "ea-01", "../auth/me", "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11/x", "0b1f1c3e0f1c4b539a1e2f6f6d0c7a11", " 0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11", "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a1g"])
      expect(esUuid(malo), malo).toBe(false);
  });
});
