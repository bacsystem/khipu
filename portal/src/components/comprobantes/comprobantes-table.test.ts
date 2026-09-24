import { describe, expect, it } from "vitest";
import { claveComprobantes } from "./comprobantes-table";

const SIN_FILTROS = { estado: undefined, desde: undefined, hasta: undefined, serie: undefined };

describe("claveComprobantes", () => {
  /**
   * Es lo único que separa ver los comprobantes propios de los de otra empresa de la misma cuenta. Cambiar de empresa es
   * una navegación blanda: la tabla no se desmonta, así que si la clave no cambia, react-query sirve las filas cacheadas
   * de la empresa anterior y descarta las que el servidor acabó de renderizar.
   */
  it("distingue dos empresas con los mismos filtros", () => {
    const a = claveComprobantes("e-uno", SIN_FILTROS, 1, 10);
    const b = claveComprobantes("e-dos", SIN_FILTROS, 1, 10);
    expect(a).not.toEqual(b);
    expect(a).toContain("e-uno");
    expect(b).toContain("e-dos");
  });

  it("distingue cada filtro, la página y el tamaño", () => {
    const base = claveComprobantes("e-uno", SIN_FILTROS, 1, 10);
    const variantes = [
      claveComprobantes("e-uno", { ...SIN_FILTROS, estado: "ACEPTADO" }, 1, 10),
      claveComprobantes("e-uno", { ...SIN_FILTROS, desde: "2026-09-01" }, 1, 10),
      claveComprobantes("e-uno", { ...SIN_FILTROS, hasta: "2026-09-30" }, 1, 10),
      claveComprobantes("e-uno", { ...SIN_FILTROS, serie: "F001" }, 1, 10),
      claveComprobantes("e-uno", SIN_FILTROS, 2, 10),
      claveComprobantes("e-uno", SIN_FILTROS, 1, 20),
    ];
    for (const v of variantes) expect(v).not.toEqual(base);
    // Todas distintas entre sí: ninguna colisión escondida.
    expect(new Set(variantes.map((v) => JSON.stringify(v))).size).toBe(variantes.length);
  });

  it("con la misma empresa y los mismos filtros la clave es estable", () => {
    expect(claveComprobantes("e-uno", { ...SIN_FILTROS, serie: "F001" }, 2, 20)).toEqual(
      claveComprobantes("e-uno", { ...SIN_FILTROS, serie: "F001" }, 2, 20),
    );
  });
});
