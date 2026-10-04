import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ErrorDeEmision, ParamsErrores } from "@/lib/api/admin-errores";
import type { ApiEnvelope } from "@/lib/api/types";
import { ColaDeErroresTabla } from "./cola-de-errores-tabla";

const push = vi.fn();
const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh }) }));
const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/browser", () => ({ apiRequest }));

const EMPRESA_A = "00000000-0000-4000-9000-000000000001";
const EMPRESA_B = "00000000-0000-4000-9000-000000000002";
const PARAMS: ParamsErrores = { pagina: 1, porPagina: 10 };

afterEach(() => {
  cleanup();
  push.mockClear();
  refresh.mockClear();
  apiRequest.mockReset();
});

const exito = (datos: unknown): ApiEnvelope<unknown> => ({ estado: "exito", datos, mensaje: null, codigo: null, errores: null });

function error(n: number, p: Partial<ErrorDeEmision> = {}): ErrorDeEmision {
  return {
    comprobante_id: `00000000-0000-4000-d000-${String(n).padStart(12, "0")}`,
    empresa_id: EMPRESA_A,
    ruc: "20100047226",
    razon_social: "PANADERIA SOL SAC",
    cuenta_id: "00000000-0000-4000-8000-000000000001",
    cuenta_nombre: "Panadería Sol",
    nombre_archivo: `20100047226-01-F001-${n}`,
    tipo: "01",
    serie: "F001",
    numero: n,
    fecha_emision: "2026-10-01",
    estado: "ERROR_ENVIO",
    clase: "ERROR_DE_ENVIO",
    intentos: 3,
    fault: { codigo: "0109", mensaje: "El sistema no puede responder" },
    proximo_intento: "2026-10-04T18:30:00Z",
    actualizado_en: "2026-10-04T15:00:00Z",
    accionable: true,
    ...p,
  };
}

const formato = (n: number) =>
  error(n, { estado: "RECHAZADO", clase: "ERROR_DE_FORMATO", intentos: 1, fault: { codigo: "1033", mensaje: "Registrado previamente" }, proximo_intento: undefined, accionable: false });
const plazo = (n: number) =>
  error(n, { estado: "FUERA_DE_PLAZO", clase: "FUERA_DE_PLAZO", fault: { codigo: "2108", mensaje: "Presentación fuera de fecha" }, proximo_intento: undefined, accionable: false });

const filas = () => screen.getAllByTestId("errores-fila");

describe("ColaDeErroresTabla (#196)", () => {
  // --- las filas ----------------------------------------------------------------------------------------------------------------------

  it("cada fila dice el cliente, el comprobante, el problema, el fault, los intentos y el próximo intento", () => {
    render(<ColaDeErroresTabla errores={[error(7)]} total={1} params={PARAMS} />);

    const fila = within(filas()[0]);
    expect(fila.getByRole("link", { name: "PANADERIA SOL SAC" }).getAttribute("href")).toBe(`/admin/empresas/${EMPRESA_A}`);
    expect(fila.getByText("20100047226")).toBeTruthy();
    expect(fila.getByText("Panadería Sol")).toBeTruthy();
    expect(fila.getByText("20100047226-01-F001-7")).toBeTruthy();
    expect(fila.getByText("Factura · 1 Oct 2026")).toBeTruthy();
    expect(fila.getByText("Error de envío", { selector: "span:not([data-slot])" })).toBeTruthy();
    expect(within(filas()[0]).getByTestId("errores-fault").textContent).toBe("0109 - El sistema no puede responder");
    expect(within(filas()[0]).getByTestId("errores-intentos").textContent).toBe("3");
    expect(fila.getByText("Próximo intento: 4 Oct 2026, 13:30")).toBeTruthy();
  });

  it("la fila lleva su clase y su comprobante para quien la necesite localizar", () => {
    render(<ColaDeErroresTabla errores={[error(7), formato(8)]} total={2} params={PARAMS} />);

    expect(filas().map((f) => [f.getAttribute("data-clase"), f.getAttribute("data-comprobante")])).toEqual([
      ["ERROR_DE_ENVIO", "20100047226-01-F001-7"],
      ["ERROR_DE_FORMATO", "20100047226-01-F001-8"],
    ]);
  });

  it("sin fault dice «Sin detalle»; con solo un mensaje, lo muestra sin código", () => {
    render(<ColaDeErroresTabla errores={[error(1, { fault: undefined }), error(2, { fault: { mensaje: "INFRA - storage no disponible" } })]} total={2} params={PARAMS} />);

    expect(within(filas()[0]).getByTestId("errores-fault").textContent).toBe("Sin detalle");
    expect(within(filas()[1]).getByTestId("errores-fault").textContent).toBe("INFRA - storage no disponible");
  });

  it("un error de envío sin reintento programado lo dice; uno terminal no dice nada del reintento", () => {
    render(<ColaDeErroresTabla errores={[error(1, { proximo_intento: undefined }), formato(2)]} total={2} params={PARAMS} />);

    expect(within(filas()[0]).getByText("Sin reintento programado")).toBeTruthy();
    expect(within(filas()[1]).queryByText("Sin reintento programado")).toBeNull();
    expect(within(filas()[1]).queryByText(/Próximo intento/)).toBeNull();
  });

  it("una empresa sin cuenta (de integración) no inventa un nombre de cuenta", () => {
    render(<ColaDeErroresTabla errores={[error(1, { cuenta_id: undefined, cuenta_nombre: undefined })]} total={1} params={PARAMS} />);

    expect(within(filas()[0]).queryByText("Panadería Sol")).toBeNull();
  });

  it("el tipo de comprobante desconocido se muestra tal cual", () => {
    render(<ColaDeErroresTabla errores={[error(1, { tipo: "99" })]} total={1} params={PARAMS} />);

    expect(within(filas()[0]).getByText("99 · 1 Oct 2026")).toBeTruthy();
  });

  // --- las acciones -------------------------------------------------------------------------------------------------------------------

  it("un error de envío ofrece reintentar y descartar", () => {
    render(<ColaDeErroresTabla errores={[error(1)]} total={1} params={PARAMS} />);

    expect(within(filas()[0]).getByTestId("errores-reintentar")).toBeTruthy();
    expect(within(filas()[0]).getByTestId("errores-descartar")).toBeTruthy();
    expect(within(filas()[0]).queryByText("Terminal: no se reintenta")).toBeNull();
  });

  it("un error de formato y un fuera de plazo son terminales: no ofrecen acciones y lo dicen", () => {
    render(<ColaDeErroresTabla errores={[formato(1), plazo(2)]} total={2} params={PARAMS} />);

    for (const f of filas()) {
      expect(within(f).queryByTestId("errores-reintentar")).toBeNull();
      expect(within(f).queryByTestId("errores-descartar")).toBeNull();
      expect(within(f).getByText("Terminal: no se reintenta")).toBeTruthy();
    }
  });

  it("lo que pasó con un reintento se dice arriba y sobrevive a la recarga de la página", async () => {
    apiRequest.mockResolvedValue(exito({ comprobante_id: "x", estado: "ERROR_ENVIO", intentos: 4, fault: { codigo: "0000", mensaje: "SUNAT respondió HTTP 503" } }));
    render(<ColaDeErroresTabla errores={[error(7)]} total={1} params={PARAMS} />);
    expect(screen.queryByTestId("errores-resultado")).toBeNull();

    fireEvent.click(within(filas()[0]).getByTestId("errores-reintentar"));
    fireEvent.click(screen.getByTestId("errores-reintentar-confirmar"));

    await waitFor(() => expect(screen.getByTestId("errores-resultado").textContent).toBe("20100047226-01-F001-7: SUNAT volvió a fallar (intento 4). 0000 - SUNAT respondió HTTP 503"));
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("errores-resultado").getAttribute("role")).toBe("status");
  });

  it("lo que pasó con un descarte también se dice arriba", async () => {
    apiRequest.mockResolvedValue(exito({ comprobante_id: "x", estado: "DESCARTADO" }));
    render(<ColaDeErroresTabla errores={[error(7)]} total={1} params={PARAMS} />);

    fireEvent.click(within(filas()[0]).getByTestId("errores-descartar"));
    fireEvent.change(screen.getByTestId("errores-motivo"), { target: { value: "ya no se emite" } });
    fireEvent.click(screen.getByTestId("errores-descartar-confirmar"));

    await waitFor(() => expect(screen.getByTestId("errores-resultado").textContent).toBe("20100047226-01-F001-7 se descartó."));
  });

  // --- los filtros --------------------------------------------------------------------------------------------------------------------

  it("las vistas son enlaces que conservan lo demás y vuelven a la primera página", () => {
    render(<ColaDeErroresTabla errores={[error(1)]} total={40} params={{ clase: "ERROR_DE_FORMATO", empresa: EMPRESA_A, q: "sol", pagina: 3, porPagina: 20 }} />);

    const vistas = within(screen.getByRole("navigation", { name: "Tipo de error" }));
    expect(vistas.getByRole("link", { name: "Todos" }).getAttribute("href")).toBe(`/admin/errores?empresa_id=${EMPRESA_A}&q=sol&por_pagina=20`);
    expect(vistas.getByRole("link", { name: "Error de envío" }).getAttribute("href")).toBe(`/admin/errores?clase=ERROR_DE_ENVIO&empresa_id=${EMPRESA_A}&q=sol&por_pagina=20`);
    expect(vistas.getByRole("link", { name: "Fuera de plazo" }).getAttribute("href")).toBe(`/admin/errores?clase=FUERA_DE_PLAZO&empresa_id=${EMPRESA_A}&q=sol&por_pagina=20`);
  });

  it("marca la vista actual: «Todos» sin filtro, o la clase elegida", () => {
    const { unmount } = render(<ColaDeErroresTabla errores={[]} total={0} params={PARAMS} />);
    const vistas = () => within(screen.getByRole("navigation", { name: "Tipo de error" }));
    expect(vistas().getByRole("link", { name: "Todos" }).getAttribute("aria-current")).toBe("page");
    expect(vistas().getByRole("link", { name: "Error de formato" }).getAttribute("aria-current")).toBeNull();
    unmount();

    render(<ColaDeErroresTabla errores={[]} total={0} params={{ ...PARAMS, clase: "ERROR_DE_FORMATO" }} />);
    expect(vistas().getByRole("link", { name: "Error de formato" }).getAttribute("aria-current")).toBe("page");
    expect(vistas().getByRole("link", { name: "Todos" }).getAttribute("aria-current")).toBeNull();
  });

  it("con una clase elegida explica qué significa; sin ella no", () => {
    const { unmount } = render(<ColaDeErroresTabla errores={[]} total={0} params={PARAMS} />);
    expect(screen.queryByText(/fault 1000–1999/)).toBeNull();
    unmount();

    render(<ColaDeErroresTabla errores={[]} total={0} params={{ ...PARAMS, clase: "ERROR_DE_FORMATO" }} />);
    expect(screen.getByText("SUNAT lo rechazó con un fault 1000–1999: no cambia por reintentar.")).toBeTruthy();
  });

  it("buscar manda el texto recortado a la URL, vuelve a la primera página y conserva la clase", () => {
    render(<ColaDeErroresTabla errores={[]} total={0} params={{ clase: "ERROR_DE_ENVIO", pagina: 4, porPagina: 20 }} />);

    fireEvent.change(screen.getByLabelText("Buscar cliente"), { target: { value: "  andina  " } });
    fireEvent.submit(screen.getByRole("search"));

    expect(push).toHaveBeenCalledWith("/admin/errores?clase=ERROR_DE_ENVIO&q=andina&por_pagina=20");
  });

  it("buscar con el campo vacío quita la búsqueda", () => {
    render(<ColaDeErroresTabla errores={[]} total={0} params={{ ...PARAMS, q: "andina" }} />);

    fireEvent.change(screen.getByLabelText("Buscar cliente"), { target: { value: "   " } });
    fireEvent.submit(screen.getByRole("search"));

    expect(push).toHaveBeenCalledWith("/admin/errores");
  });

  it("el campo de búsqueda arranca con lo que ya se buscó y tiene un tope de largo", () => {
    render(<ColaDeErroresTabla errores={[]} total={0} params={{ ...PARAMS, q: "andina" }} />);

    const campo = screen.getByLabelText("Buscar cliente") as HTMLInputElement;
    expect(campo.value).toBe("andina");
    expect(campo.maxLength).toBe(100);
  });

  it("cada fila ofrece ver solo su empresa, conservando lo demás; si ya se filtra por ella, no", () => {
    const { unmount } = render(<ColaDeErroresTabla errores={[error(1), error(2, { empresa_id: EMPRESA_B })]} total={2} params={{ ...PARAMS, clase: "ERROR_DE_ENVIO", pagina: 2 }} />);

    expect(within(filas()[0]).getByTestId("errores-filtrar-empresa").getAttribute("href")).toBe(`/admin/errores?clase=ERROR_DE_ENVIO&empresa_id=${EMPRESA_A}`);
    expect(within(filas()[1]).getByTestId("errores-filtrar-empresa").getAttribute("href")).toBe(`/admin/errores?clase=ERROR_DE_ENVIO&empresa_id=${EMPRESA_B}`);
    unmount();

    render(<ColaDeErroresTabla errores={[error(1)]} total={1} params={{ ...PARAMS, empresa: EMPRESA_A }} />);
    expect(within(filas()[0]).queryByTestId("errores-filtrar-empresa")).toBeNull();
  });

  it("con una empresa elegida lo dice y deja ver todas; sin ella no dice nada", () => {
    const { unmount } = render(<ColaDeErroresTabla errores={[]} total={0} params={PARAMS} />);
    expect(screen.queryByTestId("errores-empresa-filtrada")).toBeNull();
    unmount();

    render(<ColaDeErroresTabla errores={[]} total={0} params={{ clase: "ERROR_DE_ENVIO", empresa: EMPRESA_A, q: "sol", pagina: 3, porPagina: 20 }} />);
    const chip = within(screen.getByTestId("errores-empresa-filtrada"));
    expect(chip.getByText("Solo una empresa")).toBeTruthy();
    expect(chip.getByRole("link", { name: "Ver todas" }).getAttribute("href")).toBe("/admin/errores?clase=ERROR_DE_ENVIO&q=sol&por_pagina=20");
  });

  it("«Quitar filtros» solo aparece con algún filtro y deja el tamaño de página", () => {
    const { unmount } = render(<ColaDeErroresTabla errores={[]} total={0} params={PARAMS} />);
    expect(screen.queryByRole("link", { name: "Quitar filtros" })).toBeNull();
    unmount();

    for (const params of [{ clase: "ERROR_DE_ENVIO" as const }, { empresa: EMPRESA_A }, { q: "sol" }]) {
      const r = render(<ColaDeErroresTabla errores={[]} total={0} params={{ pagina: 2, porPagina: 50, ...params }} />);
      expect(screen.getByRole("link", { name: "Quitar filtros" }).getAttribute("href")).toBe("/admin/errores?por_pagina=50");
      r.unmount();
    }
  });

  // --- vacío y paginación -------------------------------------------------------------------------------------------------------------

  it("sin errores dice que todo está en orden, o según la clase", () => {
    const { rerender } = render(<ColaDeErroresTabla errores={[]} total={0} params={PARAMS} />);
    expect(screen.getByTestId("errores-vacio").textContent).toBe("No hay comprobantes con problema. Todo en orden.");

    rerender(<ColaDeErroresTabla errores={[]} total={0} params={{ ...PARAMS, clase: "ERROR_DE_ENVIO" }} />);
    expect(screen.getByTestId("errores-vacio").textContent).toBe("No hay errores de envío.");
    rerender(<ColaDeErroresTabla errores={[]} total={0} params={{ ...PARAMS, clase: "ERROR_DE_FORMATO" }} />);
    expect(screen.getByTestId("errores-vacio").textContent).toBe("No hay errores de formato.");
    rerender(<ColaDeErroresTabla errores={[]} total={0} params={{ ...PARAMS, clase: "FUERA_DE_PLAZO" }} />);
    expect(screen.getByTestId("errores-vacio").textContent).toBe("No hay comprobantes fuera de plazo.");
  });

  it("con una búsqueda o una empresa que no encuentra nada lo dice distinto: no es que todo esté en orden", () => {
    const { rerender } = render(<ColaDeErroresTabla errores={[]} total={0} params={{ ...PARAMS, q: "zzz" }} />);
    expect(screen.getByTestId("errores-vacio").textContent).toBe("Ningún comprobante con problema coincide con la búsqueda.");

    rerender(<ColaDeErroresTabla errores={[]} total={0} params={{ ...PARAMS, empresa: EMPRESA_A }} />);
    expect(screen.getByTestId("errores-vacio").textContent).toBe("Ningún comprobante con problema coincide con la búsqueda.");
  });

  it("con filas no muestra el estado vacío", () => {
    render(<ColaDeErroresTabla errores={[error(1)]} total={1} params={PARAMS} />);

    expect(screen.queryByTestId("errores-vacio")).toBeNull();
  });

  it("el pie dice qué filas se ven del total y la paginación conserva los filtros", () => {
    render(<ColaDeErroresTabla errores={[error(1), error(2)]} total={42} params={{ clase: "ERROR_DE_ENVIO", q: "sol", pagina: 2, porPagina: 10 }} />);

    expect(screen.getByText("11–12")).toBeTruthy();
    expect(screen.getByText("42")).toBeTruthy();
    const siguiente = screen.getAllByRole("link").find((a) => a.getAttribute("href")?.includes("pagina=3"));
    expect(siguiente?.getAttribute("href")).toBe("/admin/errores?clase=ERROR_DE_ENVIO&q=sol&pagina=3");
  });

  it("un clic en una página navega sin recargar", () => {
    render(<ColaDeErroresTabla errores={[error(1)]} total={42} params={{ ...PARAMS, clase: "ERROR_DE_ENVIO", pagina: 2 }} />);

    fireEvent.click(screen.getAllByRole("link").find((a) => a.getAttribute("href")?.includes("pagina=3")) as HTMLElement);

    expect(push).toHaveBeenCalledWith("/admin/errores?clase=ERROR_DE_ENVIO&pagina=3");
  });

  it("el cambio de filas por página vuelve a la primera página", () => {
    render(<ColaDeErroresTabla errores={[error(1)]} total={90} params={{ ...PARAMS, pagina: 4 }} />);

    fireEvent.click(within(screen.getByRole("group", { name: "Filas por página" })).getByRole("button", { name: "50" }));

    expect(push).toHaveBeenCalledWith("/admin/errores?por_pagina=50");
  });
});
