import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { fijarLocaleDePrueba } from "@/i18n/prueba";
import { DESCARGA_MAXIMA, DescargaMasiva, hrefDeDescarga } from "./descarga-masiva";

afterEach(cleanup);

const abrir = () => fireEvent.click(screen.getByRole("button", { name: "Descargar ZIP" }));

describe("DescargaMasiva (#324)", () => {
  it("el enlace lleva los filtros activos de la tabla y los archivos elegidos, no solo la página", () => {
    render(<DescargaMasiva filtros={{ estado: "ACEPTADO", desde: "2026-09-01", hasta: "2026-09-30", serie: "F001", tipo: "01", cliente: "andina", numero: 7 }} total={42} />);
    abrir();
    const href = new URL(screen.getByTestId("descarga-enlace").getAttribute("href")!, "http://x");
    expect(href.pathname).toBe("/api/proxy/facturas/descarga");
    expect(Object.fromEntries(href.searchParams)).toEqual({ estado: "ACEPTADO", desde: "2026-09-01", hasta: "2026-09-30", serie: "F001", tipo: "01", cliente: "andina", numero: "7", tipos: "pdf,xml,cdr" });
    expect(screen.getByTestId("descarga-masiva")).toHaveTextContent("42 comprobantes");
  });

  it("se eligen los archivos: quitar uno cambia el enlace y sin ninguno no hay enlace", () => {
    render(<DescargaMasiva filtros={{}} total={3} />);
    abrir();
    fireEvent.click(screen.getByRole("checkbox", { name: "CDR de SUNAT" }));
    expect(new URL(screen.getByTestId("descarga-enlace").getAttribute("href")!, "http://x").searchParams.get("tipos")).toBe("pdf,xml");
    fireEvent.click(screen.getByRole("checkbox", { name: "PDF" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "XML firmado" }));
    expect(screen.queryByTestId("descarga-enlace")).not.toBeInTheDocument();
    expect(screen.getByText("Elige al menos un archivo")).toBeInTheDocument();
  });

  it("por encima del tope no se ofrece y dice cuántos hay; sin resultados, tampoco", () => {
    const { unmount } = render(<DescargaMasiva filtros={{}} total={DESCARGA_MAXIMA + 1} />);
    abrir();
    expect(screen.queryByTestId("descarga-enlace")).not.toBeInTheDocument();
    expect(screen.getByTestId("descarga-motivo")).toHaveTextContent(`Hay ${DESCARGA_MAXIMA + 1}: una descarga admite hasta ${DESCARGA_MAXIMA}`);
    unmount();
    render(<DescargaMasiva filtros={{}} total={DESCARGA_MAXIMA} />);
    abrir();
    expect(screen.getByTestId("descarga-enlace")).toBeInTheDocument();
    cleanup();
    render(<DescargaMasiva filtros={{}} total={0} />);
    abrir();
    expect(screen.getByTestId("descarga-motivo")).toHaveTextContent("No hay comprobantes con estos filtros");
  });

  it("en inglés (#25): el botón, los archivos y el tope se dicen en inglés", () => {
    fijarLocaleDePrueba("en");
    render(<DescargaMasiva filtros={{}} total={DESCARGA_MAXIMA + 1} />);
    fireEvent.click(screen.getByRole("button", { name: "Download ZIP" }));
    expect(screen.getByRole("checkbox", { name: "Signed XML" })).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "SUNAT CDR" })).toBeInTheDocument();
    expect(screen.getByTestId("descarga-motivo")).toHaveTextContent(`There are ${DESCARGA_MAXIMA + 1}: a download allows up to ${DESCARGA_MAXIMA}. Narrow the dates.`);
  });

  it("hrefDeDescarga no arma nada sin archivos", () => {
    expect(hrefDeDescarga({}, [])).toBeNull();
    expect(hrefDeDescarga({}, ["xml"])).toBe("/api/proxy/facturas/descarga?tipos=xml");
  });
});
