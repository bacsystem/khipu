import { describe, expect, it } from "vitest";
import { cn } from "./utils";

/**
 * #510: la escala tipográfica del design system (`text-etiqueta`, `text-control`, `text-cuerpo`, `text-titulo`…) es de tamaño, no de color. El `cn` genérico (`tailwind-merge` sin
 * configurar) las toma por colores de texto y borra el tamaño o el color al combinarlas; el del kit las conoce. Por eso `cn` sale del paquete.
 */
describe("cn", () => {
  it("combina clases y descarta valores falsy", () => {
    expect(cn("a", false && "b", "c")).toBe("a c");
  });

  it("conserva el tamaño de la escala junto a un color de texto", () => {
    expect(cn("text-etiqueta text-muted-foreground")).toBe("text-etiqueta text-muted-foreground");
    expect(cn("font-mono text-control tracking-[0.2em] text-muted-foreground/50")).toContain("text-control");
    expect(cn("text-cuerpo font-semibold text-destructive")).toBe("text-cuerpo font-semibold text-destructive");
    expect(cn("text-titulo-pagina text-foreground")).toBe("text-titulo-pagina text-foreground");
  });

  it("sigue resolviendo los conflictos de verdad: el último tamaño y el último color ganan", () => {
    expect(cn("text-control text-cuerpo")).toBe("text-cuerpo");
    expect(cn("text-foreground text-muted-foreground")).toBe("text-muted-foreground");
    expect(cn("px-3", "px-4")).toBe("px-4");
  });
});
