import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import type { DocumentoLegal } from "@/content/legal/tipos";
import { DocumentoLegalVista } from "./documento-legal";

afterEach(cleanup);

const DOC: DocumentoLegal = {
  titulo: "Términos de prueba",
  version: "2026-10-20",
  resumen: "Un resumen.",
  secciones: [
    { titulo: "1. Primera", parrafos: ["Un párrafo."], lista: ["Un punto", "Otro punto"], cierre: ["Al final."] },
    { titulo: "2. Segunda", parrafos: ["Otro párrafo."] },
  ],
};
const OTRO = { href: "/privacidad", titulo: "Política de privacidad" };

describe("DocumentoLegalVista (#342)", () => {
  it("pinta el título, la versión, las secciones y el enlace al otro texto", () => {
    render(<DocumentoLegalVista documento={DOC} otro={OTRO} revisado />);

    expect(screen.getByRole("heading", { level: 1, name: "Términos de prueba" })).toBeTruthy();
    expect(screen.getByText("20 Oct 2026").getAttribute("datetime")).toBe("2026-10-20");
    expect(screen.getAllByRole("heading", { level: 2 }).map((h) => h.textContent)).toEqual(["1. Primera", "2. Segunda"]);
    expect(screen.getAllByRole("listitem").map((li) => li.textContent)).toEqual(["Un punto", "Otro punto"]);
    expect(screen.getByText("Al final.")).toBeTruthy();
    expect(screen.getByRole("link", { name: "Política de privacidad" }).getAttribute("href")).toBe("/privacidad");
  });

  it("mientras no lo revise un abogado dice que es un borrador, arriba de todo", () => {
    render(<DocumentoLegalVista documento={DOC} otro={OTRO} revisado={false} />);

    const aviso = screen.getByTestId("borrador-legal");
    expect(aviso.textContent).toContain("Borrador pendiente de revisión legal");
    expect(aviso.compareDocumentPosition(screen.getByRole("heading", { level: 1 })) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("revisado, el aviso desaparece", () => {
    render(<DocumentoLegalVista documento={DOC} otro={OTRO} revisado />);

    expect(screen.queryByTestId("borrador-legal")).toBeNull();
  });
});
