import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ConsumoDeCuentas, CuentaConsumo, ParamsConsumo } from "@/lib/api/admin-consumo";
import { ConsumoTabla } from "./consumo-tabla";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

afterEach(() => {
  cleanup();
  push.mockClear();
});

const PARAMS: ParamsConsumo = { filtro: "TODAS", orden: "PORCENTAJE", pagina: 1, porPagina: 10 };

const ANA: CuentaConsumo = {
  cuenta_id: "00000000-0000-4000-8000-000000000001",
  nombre: "Ana Quispe",
  email: "ana@negocio.pe",
  plan_id: "p1",
  plan: "Emprende",
  documentos: 240,
  limite: 300,
  porcentaje: 80,
  en_alerta: true,
  estado_del_plan: "EN_GRACIA",
  pagado_hasta: "2026-10-21T05:00:00Z",
  se_sirve_hasta: "2026-10-26T05:00:00Z",
};
const LUIS: CuentaConsumo = { cuenta_id: "00000000-0000-4000-8000-000000000002", nombre: "Luis Rojas", email: "luis@otro.pe", plan_id: "p2", plan: "Gratis", documentos: 3, limite: 30, porcentaje: 10, en_alerta: false, estado_del_plan: "VIGENTE" };
const PRO: CuentaConsumo = { cuenta_id: "00000000-0000-4000-8000-000000000003", nombre: "Tienda Pro", email: "pro@tienda.pe", plan_id: "p3", plan: "Pro", documentos: 9000, en_alerta: false, estado_del_plan: "VIGENTE" };
const TOPE: CuentaConsumo = { ...ANA, cuenta_id: "00000000-0000-4000-8000-000000000004", nombre: "Sobrepasada", documentos: 450, porcentaje: 150, estado_del_plan: "VENCIDA" };

const datos = (cuentas: CuentaConsumo[], mes = "2026-10"): ConsumoDeCuentas => ({ mes, umbral_de_alerta: 80, cuentas });

const fila = (nombre: string) => screen.getByText(nombre).closest("tr") as HTMLElement;

describe("ConsumoTabla (#193)", () => {
  it("cada cuenta dice su plan, lo que consumió, su tope, su uso y el estado de su plan", () => {
    render(<ConsumoTabla datos={datos([ANA, LUIS])} total={2} params={PARAMS} />);

    const ana = within(fila("Ana Quispe"));
    expect(ana.getByRole("link", { name: "Ana Quispe" }).getAttribute("href")).toBe("/admin/cuentas/00000000-0000-4000-8000-000000000001");
    expect(ana.getByText("ana@negocio.pe")).toBeTruthy();
    expect(ana.getByText("Emprende")).toBeTruthy();
    expect(ana.getByText("240")).toBeTruthy();
    expect(ana.getByText("300")).toBeTruthy();
    expect(ana.getByText("80 %")).toBeTruthy();
    expect(ana.getByText("En gracia")).toBeTruthy();
    expect(ana.getByText(/Hasta el 20 Oct 2026/)).toBeTruthy();
    expect(within(fila("Luis Rojas")).getByText("Al día")).toBeTruthy();
  });

  it("los miles se separan con coma, como en el resto del portal", () => {
    render(<ConsumoTabla datos={datos([PRO])} total={1} params={PARAMS} />);

    expect(within(fila("Tienda Pro")).getByText("9,000")).toBeTruthy();
  });

  /** La alerta es del backend (`en_alerta`); la tabla no la recalcula con su propio umbral. */
  it("una cuenta en alerta lo dice con etiqueta y marca; una tranquila no", () => {
    render(<ConsumoTabla datos={datos([ANA, LUIS])} total={2} params={PARAMS} />);

    expect(fila("Ana Quispe").getAttribute("data-alerta")).toBe("true");
    expect(within(fila("Ana Quispe")).getByText("Cerca del límite")).toBeTruthy();
    expect(fila("Luis Rojas").getAttribute("data-alerta")).toBe("false");
    expect(within(fila("Luis Rojas")).queryByText("Cerca del límite")).toBeNull();
  });

  it("una cuenta al tope o por encima dice «En el límite» y no «Cerca»", () => {
    render(<ConsumoTabla datos={datos([TOPE])} total={1} params={PARAMS} />);

    expect(within(fila("Sobrepasada")).getByText("En el límite")).toBeTruthy();
    expect(within(fila("Sobrepasada")).queryByText("Cerca del límite")).toBeNull();
    expect(within(fila("Sobrepasada")).getByText("150 %")).toBeTruthy();
    expect(within(fila("Sobrepasada")).getByText("Vencido")).toBeTruthy();
  });

  it("la barra de uso refleja el porcentaje y no pasa de lleno", () => {
    render(<ConsumoTabla datos={datos([LUIS, TOPE])} total={2} params={PARAMS} />);

    const luis = within(fila("Luis Rojas")).getByRole("progressbar");
    expect(luis.getAttribute("aria-valuenow")).toBe("10");
    expect((luis.firstElementChild as HTMLElement).style.width).toBe("10%");
    const tope = within(fila("Sobrepasada")).getByRole("progressbar");
    expect(tope.getAttribute("aria-valuenow")).toBe("100");
    expect((tope.firstElementChild as HTMLElement).style.width).toBe("100%");
  });

  /** Lo omitido nunca se lee como cero: un plan sin tope no tiene porcentaje ni barra. */
  /** El color dice de un vistazo cuánto falta: tranquila (primario), cerca del límite (aviso) o ya en él (error). */
  it("la barra y las etiquetas tienen el tono del estado: tranquila, cerca del límite y en el límite", () => {
    render(<ConsumoTabla datos={datos([LUIS, ANA, TOPE])} total={3} params={PARAMS} />);

    const relleno = (nombre: string) => (within(fila(nombre)).getByRole("progressbar").firstElementChild as HTMLElement).className;
    expect(relleno("Luis Rojas")).toContain("bg-primary");
    expect(relleno("Ana Quispe")).toContain("bg-warning-foreground");
    expect(relleno("Sobrepasada")).toContain("bg-destructive");
    expect(within(fila("Ana Quispe")).getByText("Cerca del límite").className).toContain("bg-warning");
    expect(within(fila("Sobrepasada")).getByText("En el límite").className).toContain("text-destructive");
  });

  it("el estado del plan tiene su tono: al día en verde, en gracia en aviso y vencido en rojo", () => {
    render(<ConsumoTabla datos={datos([LUIS, ANA, TOPE])} total={3} params={PARAMS} />);

    const etiqueta = (nombre: string, texto: string) => within(fila(nombre)).getByText(texto).className;
    expect(etiqueta("Luis Rojas", "Al día")).toContain("bg-success");
    expect(etiqueta("Ana Quispe", "En gracia")).toContain("bg-warning");
    expect(etiqueta("Sobrepasada", "Vencido")).toContain("text-destructive");
  });

  it("un plan sin tope dice «Ilimitado» y «Sin tope», sin porcentaje ni barra", () => {
    render(<ConsumoTabla datos={datos([PRO])} total={1} params={PARAMS} />);

    const pro = within(fila("Tienda Pro"));
    expect(pro.getByText("Ilimitado")).toBeTruthy();
    expect(pro.getByText("Sin tope")).toBeTruthy();
    expect(pro.queryByRole("progressbar")).toBeNull();
    expect(pro.queryByText(/%/)).toBeNull();
  });

  it("un plan al día sin fecha de vencimiento no dice «hasta» ninguna fecha", () => {
    render(<ConsumoTabla datos={datos([LUIS])} total={1} params={PARAMS} />);

    expect(within(fila("Luis Rojas")).queryByText(/Hasta el/)).toBeNull();
  });

  it("el estado del plan se distingue sin leer el texto", () => {
    render(<ConsumoTabla datos={datos([ANA, LUIS, TOPE])} total={3} params={PARAMS} />);

    expect(fila("Ana Quispe").querySelector("[data-estado]")?.getAttribute("data-estado")).toBe("EN_GRACIA");
    expect(fila("Luis Rojas").querySelector("[data-estado]")?.getAttribute("data-estado")).toBe("VIGENTE");
    expect(fila("Sobrepasada").querySelector("[data-estado]")?.getAttribute("data-estado")).toBe("VENCIDA");
  });

  // --- el filtro --------------------------------------------------------------------------------------------------------------------------

  it("las tres vistas son enlaces; la actual está marcada y las otras vuelven a la primera página conservando mes y orden", () => {
    render(<ConsumoTabla datos={datos([ANA])} total={40} params={{ mes: "2026-09", filtro: "CERCA_DEL_LIMITE", orden: "DOCUMENTOS", pagina: 3, porPagina: 20 }} />);

    const vistas = within(screen.getByRole("navigation", { name: "Cuentas a mostrar" }));
    expect(vistas.getByRole("link", { name: "Cerca del límite" }).getAttribute("aria-current")).toBe("page");
    expect(vistas.getByRole("link", { name: "Todas" }).getAttribute("aria-current")).toBeNull();
    expect(vistas.getByRole("link", { name: "Todas" }).getAttribute("href")).toBe("/admin/consumo?mes=2026-09&orden=DOCUMENTOS&por_pagina=20");
    expect(vistas.getByRole("link", { name: "Plan vencido" }).getAttribute("href")).toBe("/admin/consumo?mes=2026-09&filtro=PLAN_VENCIDO&orden=DOCUMENTOS&por_pagina=20");
  });

  it("explica el umbral de alerta con el que manda el backend y que se compara con el plan de hoy", () => {
    render(<ConsumoTabla datos={{ ...datos([ANA]), umbral_de_alerta: 85 }} total={1} params={PARAMS} />);

    expect(screen.getByText(/desde el 85 % del tope/)).toBeTruthy();
    expect(screen.getByText(/plan de hoy de cada cuenta/)).toBeTruthy();
  });

  // --- el mes y el orden ------------------------------------------------------------------------------------------------------------------

  it("el selector de mes muestra el mes que se midió, también cuando es el mes en curso sin pedirlo", () => {
    render(<ConsumoTabla datos={datos([ANA], "2026-10")} total={1} params={PARAMS} />);

    expect((screen.getByLabelText("Mes") as HTMLInputElement).value).toBe("2026-10");
  });

  it("elegir otro mes desde la página 3 navega a la primera y conserva filtro, orden y tamaño", () => {
    render(<ConsumoTabla datos={datos([ANA])} total={90} params={{ filtro: "PLAN_VENCIDO", orden: "DOCUMENTOS", pagina: 3, porPagina: 20 }} />);

    fireEvent.change(screen.getByLabelText("Mes"), { target: { value: "2026-08" } });

    expect(push).toHaveBeenCalledWith("/admin/consumo?mes=2026-08&filtro=PLAN_VENCIDO&orden=DOCUMENTOS&por_pagina=20");
  });

  it("borrar el mes vuelve al mes en curso, sin mes en la URL", () => {
    render(<ConsumoTabla datos={datos([ANA])} total={1} params={{ ...PARAMS, mes: "2026-08" }} />);

    fireEvent.change(screen.getByLabelText("Mes"), { target: { value: "" } });

    expect(push).toHaveBeenCalledWith("/admin/consumo");
  });

  it("cambiar el orden navega a la primera página y conserva el mes y el filtro", () => {
    render(<ConsumoTabla datos={datos([ANA])} total={90} params={{ mes: "2026-08", filtro: "CERCA_DEL_LIMITE", orden: "PORCENTAJE", pagina: 4, porPagina: 10 }} />);

    fireEvent.change(screen.getByLabelText("Ordenar por"), { target: { value: "DOCUMENTOS" } });

    expect(push).toHaveBeenCalledWith("/admin/consumo?mes=2026-08&filtro=CERCA_DEL_LIMITE&orden=DOCUMENTOS");
  });

  // --- la exportación ---------------------------------------------------------------------------------------------------------------------

  /** Lo que se baja es lo que se ve: el mes que se midió (aunque sea el en curso), el filtro y el orden, sin página. */
  it("exportar apunta al BFF con el mes que se midió, el filtro y el orden, sin página", () => {
    render(<ConsumoTabla datos={datos([ANA], "2026-10")} total={90} params={{ filtro: "PLAN_VENCIDO", orden: "DOCUMENTOS", pagina: 3, porPagina: 20 }} />);

    const enlace = screen.getByRole("link", { name: "Exportar CSV" });
    expect(enlace.getAttribute("href")).toBe("/api/admin/consumo/exportacion?mes=2026-10&filtro=PLAN_VENCIDO&orden=DOCUMENTOS");
    expect(enlace.hasAttribute("download")).toBe(true);
  });

  // --- vacío y paginación -----------------------------------------------------------------------------------------------------------------

  it("sin cuentas dice por qué, según la vista", () => {
    const { rerender } = render(<ConsumoTabla datos={datos([])} total={0} params={PARAMS} />);
    expect(screen.getByText("Todavía no hay cuentas.")).toBeTruthy();

    rerender(<ConsumoTabla datos={datos([])} total={0} params={{ ...PARAMS, filtro: "CERCA_DEL_LIMITE" }} />);
    expect(screen.getByText("Ninguna cuenta está cerca de su límite este mes.")).toBeTruthy();

    rerender(<ConsumoTabla datos={datos([])} total={0} params={{ ...PARAMS, filtro: "PLAN_VENCIDO" }} />);
    expect(screen.getByText("Ninguna cuenta tiene el plan vencido.")).toBeTruthy();
  });

  it("el pie dice qué cuentas se ven del total y la paginación conserva mes, filtro y orden", () => {
    render(<ConsumoTabla datos={datos([ANA, LUIS])} total={42} params={{ mes: "2026-09", filtro: "CERCA_DEL_LIMITE", orden: "DOCUMENTOS", pagina: 2, porPagina: 10 }} />);

    expect(screen.getByText("11–12")).toBeTruthy();
    expect(screen.getByText("42")).toBeTruthy();
    const siguiente = screen.getAllByRole("link").find((a) => a.getAttribute("href")?.includes("pagina=3"));
    expect(siguiente?.getAttribute("href")).toBe("/admin/consumo?mes=2026-09&filtro=CERCA_DEL_LIMITE&orden=DOCUMENTOS&pagina=3");
  });

  it("el cambio de filas por página vuelve a la primera página", () => {
    render(<ConsumoTabla datos={datos([ANA])} total={90} params={{ ...PARAMS, pagina: 4 }} />);

    fireEvent.click(within(screen.getByRole("group", { name: "Filas por página" })).getByRole("button", { name: "50" }));

    expect(push).toHaveBeenCalledWith("/admin/consumo?por_pagina=50");
  });
});
