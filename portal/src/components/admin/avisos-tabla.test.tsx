import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { CertificadoEnRiesgo, ParamsAvisos, SolFallando } from "@/lib/api/admin-avisos";
import type { ApiEnvelope } from "@/lib/api/types";
import { AvisosTabla } from "./avisos-tabla";

const push = vi.fn();
const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh }) }));
const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/browser", () => ({ apiRequest }));

const EMPRESA = "00000000-0000-4000-9000-000000000001";
const CERTIFICADOS: ParamsAvisos = { vista: "CERTIFICADOS", pagina: 1, porPagina: 10 };
const SOL: ParamsAvisos = { vista: "CREDENCIALES_SOL", pagina: 1, porPagina: 10 };
const CUENTA = { id: "00000000-0000-4000-8000-000000000001", nombre: "Panadería Sol", email: "panaderia@sol.pe" };

afterEach(() => {
  cleanup();
  push.mockClear();
  refresh.mockClear();
  apiRequest.mockReset();
});

const exito = (datos: unknown): ApiEnvelope<unknown> => ({ estado: "exito", datos, mensaje: null, codigo: null, errores: null });

function certificado(p: Partial<CertificadoEnRiesgo> = {}): CertificadoEnRiesgo {
  return {
    empresa_id: EMPRESA,
    ruc: "20100047226",
    razon_social: "PANADERIA SOL SAC",
    cuenta: CUENTA,
    motivo: "CERTIFICADO_POR_VENCER",
    vigente_hasta: "2026-10-14",
    dias_restantes: 10,
    puede_avisar: true,
    ...p,
  };
}

function sol(p: Partial<SolFallando> = {}): SolFallando {
  return {
    empresa_id: EMPRESA,
    ruc: "20100047226",
    razon_social: "PANADERIA SOL SAC",
    cuenta: CUENTA,
    comprobantes_afectados: 5,
    ultimo_fallo: "2026-10-04T15:00:00Z",
    ultimo_error: "0102 - Usuario o contraseña incorrectos",
    puede_avisar: true,
    ...p,
  };
}

const pagina = <T,>(filas: T[], total = filas.length) => ({ filas, total });
const filas = () => screen.getAllByTestId("avisos-fila");

describe("AvisosTabla (#197)", () => {
  // --- certificados -------------------------------------------------------------------------------------------------------------------

  it("una fila de certificado dice la empresa, el RUC, la cuenta, el estado y cuándo vence", () => {
    render(<AvisosTabla params={CERTIFICADOS} certificados={pagina([certificado()])} sol={null} />);

    const fila = within(filas()[0]);
    expect(fila.getByRole("link", { name: "PANADERIA SOL SAC" }).getAttribute("href")).toBe(`/admin/empresas/${EMPRESA}`);
    expect(fila.getByText("20100047226")).toBeTruthy();
    expect(fila.getByText("Panadería Sol · panaderia@sol.pe")).toBeTruthy();
    expect(fila.getByText("Por vencer")).toBeTruthy();
    expect(fila.getByText("Vence el 14 Oct 2026 (en 10 días)")).toBeTruthy();
  });

  it("la fila lleva su RUC y su motivo para quien la necesite localizar", () => {
    render(<AvisosTabla params={CERTIFICADOS} certificados={pagina([certificado(), certificado({ empresa_id: "otra", ruc: "20100055121", motivo: "CERTIFICADO_VENCIDO", dias_restantes: -5 })])} sol={null} />);

    expect(filas().map((f) => [f.getAttribute("data-empresa"), f.getAttribute("data-motivo")])).toEqual([
      ["20100047226", "CERTIFICADO_POR_VENCER"],
      ["20100055121", "CERTIFICADO_VENCIDO"],
    ]);
  });

  it("vencido es de error y por vencer es de aviso, y el vencido dice hace cuánto", () => {
    render(<AvisosTabla params={CERTIFICADOS} certificados={pagina([certificado(), certificado({ empresa_id: "b", motivo: "CERTIFICADO_VENCIDO", vigente_hasta: "2026-09-29", dias_restantes: -5 })])} sol={null} />);
    // «Set», no «Sep»: así abrevia el mes el formato del portal.

    const etiqueta = (i: number) => filas()[i].querySelector('[data-slot="badge"]')?.className ?? "";
    expect(etiqueta(0)).toContain("bg-warning");
    expect(etiqueta(1)).toContain("bg-destructive/10");
    expect(etiqueta(0)).not.toContain("bg-destructive/10");
    expect(within(filas()[1]).getByText("Venció el 29 Set 2026 (hace 5 días)")).toBeTruthy();
  });

  it("la vista de certificados no muestra lo de las credenciales SOL aunque lleguen", () => {
    render(<AvisosTabla params={CERTIFICADOS} certificados={pagina([certificado()])} sol={pagina([sol({ ruc: "20999999999" })])} />);

    expect(filas()).toHaveLength(1);
    expect(screen.queryByTestId("avisos-sunat")).toBeNull();
  });

  // --- credenciales SOL ---------------------------------------------------------------------------------------------------------------

  it("una fila de SOL dice cuántos comprobantes están atascados, el último fallo y lo que dijo SUNAT", () => {
    render(<AvisosTabla params={SOL} certificados={null} sol={pagina([sol()])} />);

    const fila = within(filas()[0]);
    expect(fila.getByText("5 comprobantes atascados")).toBeTruthy();
    expect(fila.getByText("Último fallo: 4 Oct 2026, 10:00")).toBeTruthy();
    expect(fila.getByTestId("avisos-sunat").textContent).toBe("SUNAT dijo: 0102 - Usuario o contraseña incorrectos");
    expect(filas()[0].getAttribute("data-motivo")).toBe("CREDENCIALES_SOL_INVALIDAS");
  });

  it("uno solo dice «1 comprobante atascado»", () => {
    render(<AvisosTabla params={SOL} certificados={null} sol={pagina([sol({ comprobantes_afectados: 1 })])} />);

    expect(within(filas()[0]).getByText("1 comprobante atascado")).toBeTruthy();
  });

  it("la vista de SOL no muestra lo de los certificados aunque lleguen", () => {
    render(<AvisosTabla params={SOL} certificados={pagina([certificado({ ruc: "20999999999" })])} sol={pagina([sol()])} />);

    expect(filas()).toHaveLength(1);
    expect(filas()[0].getAttribute("data-empresa")).toBe("20100047226");
  });

  // --- el último aviso y lo que se puede hacer ----------------------------------------------------------------------------------------

  it("sin avisos previos lo dice; con uno dice cuándo y a quién", () => {
    render(
      <AvisosTabla
        params={CERTIFICADOS}
        certificados={pagina([certificado(), certificado({ empresa_id: "b", ruc: "20100055121", ultimo_aviso: { enviado_en: "2026-10-02T15:00:00Z", destinatario: "ferreteria@luna.pe" }, puede_avisar: false, avisar_desde: "2026-10-09T15:00:00Z" })])}
        sol={null}
      />,
    );

    expect(within(filas()[0]).getByTestId("avisos-ultimo").textContent).toBe("Sin avisos");
    expect(within(filas()[1]).getByTestId("avisos-ultimo").textContent).toBe("Avisado el 2 Oct 2026, 10:00 a ferreteria@luna.pe");
  });

  it("si se puede avisar ofrece el botón y no dice nada de esperar", () => {
    render(<AvisosTabla params={CERTIFICADOS} certificados={pagina([certificado()])} sol={null} />);

    expect(within(filas()[0]).getByTestId("avisos-avisar")).toBeTruthy();
    expect(within(filas()[0]).queryByTestId("avisos-espera")).toBeNull();
    expect(within(filas()[0]).queryByTestId("avisos-sin-cuenta")).toBeNull();
  });

  it("si ya se avisó no ofrece el botón y dice desde cuándo se puede repetir", () => {
    render(<AvisosTabla params={CERTIFICADOS} certificados={pagina([certificado({ puede_avisar: false, avisar_desde: "2026-10-09T15:00:00Z" })])} sol={null} />);

    expect(within(filas()[0]).queryByTestId("avisos-avisar")).toBeNull();
    expect(within(filas()[0]).getByTestId("avisos-espera").textContent).toBe("Se puede repetir desde el 9 Oct 2026, 10:00");
  });

  it("sin cuenta no hay a quién avisarle: ni botón ni espera, y lo dice", () => {
    render(<AvisosTabla params={CERTIFICADOS} certificados={pagina([certificado({ cuenta: undefined, puede_avisar: false })])} sol={null} />);

    expect(within(filas()[0]).queryByTestId("avisos-avisar")).toBeNull();
    expect(within(filas()[0]).queryByTestId("avisos-espera")).toBeNull();
    expect(within(filas()[0]).getByTestId("avisos-sin-cuenta").textContent).toBe("Sin cuenta: no hay a quién avisarle");
    expect(within(filas()[0]).queryByText(/·/)).toBeNull();
  });

  it("un aviso que sale se dice arriba, con el correo y la empresa, y sobrevive a la recarga", async () => {
    apiRequest.mockResolvedValue(exito({ empresa_id: EMPRESA, motivo: "CERTIFICADO_POR_VENCER", destinatario: "panaderia@sol.pe", enviado_en: "x", avisar_desde: "y" }));
    render(<AvisosTabla params={CERTIFICADOS} certificados={pagina([certificado()])} sol={null} />);
    expect(screen.queryByTestId("avisos-resultado")).toBeNull();

    fireEvent.click(within(filas()[0]).getByTestId("avisos-avisar"));
    fireEvent.click(screen.getByTestId("avisos-avisar-confirmar"));

    await waitFor(() => expect(screen.getByTestId("avisos-resultado").textContent).toBe("Se le avisó a panaderia@sol.pe (PANADERIA SOL SAC)."));
    expect(screen.getByTestId("avisos-resultado").getAttribute("role")).toBe("status");
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("el botón de una fila de SOL avisa de las credenciales", async () => {
    apiRequest.mockResolvedValue(exito({ empresa_id: EMPRESA, motivo: "CREDENCIALES_SOL_INVALIDAS", destinatario: "panaderia@sol.pe", enviado_en: "x", avisar_desde: "y" }));
    render(<AvisosTabla params={SOL} certificados={null} sol={pagina([sol()])} />);

    fireEvent.click(within(filas()[0]).getByTestId("avisos-avisar"));
    fireEvent.click(screen.getByTestId("avisos-avisar-confirmar"));

    await waitFor(() => expect(apiRequest).toHaveBeenCalledWith(`/api/admin/empresas/${EMPRESA}/avisos`, { method: "POST", body: { tipo: "CREDENCIALES_SOL" } }));
  });

  // --- vistas, vacío y paginación -----------------------------------------------------------------------------------------------------

  it("las vistas son enlaces que vuelven a la primera página y conservan el tamaño de página", () => {
    render(<AvisosTabla params={{ vista: "CERTIFICADOS", pagina: 3, porPagina: 20 }} certificados={pagina([certificado()], 60)} sol={null} />);

    const vistas = within(screen.getByRole("navigation", { name: "Qué avisar" }));
    expect(vistas.getByRole("link", { name: "Certificados" }).getAttribute("href")).toBe("/admin/avisos?por_pagina=20");
    expect(vistas.getByRole("link", { name: "Credenciales SOL" }).getAttribute("href")).toBe("/admin/avisos?vista=CREDENCIALES_SOL&por_pagina=20");
  });

  it("marca la vista actual y explica qué lista es", () => {
    const { unmount } = render(<AvisosTabla params={CERTIFICADOS} certificados={pagina([])} sol={null} />);
    const vistas = () => within(screen.getByRole("navigation", { name: "Qué avisar" }));
    expect(vistas().getByRole("link", { name: "Certificados" }).getAttribute("aria-current")).toBe("page");
    expect(vistas().getByRole("link", { name: "Credenciales SOL" }).getAttribute("aria-current")).toBeNull();
    expect(screen.getByText(/Certificados que vencen en menos de 30 días/)).toBeTruthy();
    unmount();

    render(<AvisosTabla params={SOL} certificados={null} sol={pagina([])} />);
    expect(vistas().getByRole("link", { name: "Credenciales SOL" }).getAttribute("aria-current")).toBe("page");
    expect(vistas().getByRole("link", { name: "Certificados" }).getAttribute("aria-current")).toBeNull();
    expect(screen.getByText(/Se deduce de los envíos que fallan ahora/)).toBeTruthy();
  });

  it("sin nada que avisar dice que todo está en orden, según la vista", () => {
    const { unmount } = render(<AvisosTabla params={CERTIFICADOS} certificados={pagina([])} sol={null} />);
    expect(screen.getByTestId("avisos-vacio").textContent).toBe("Ningún certificado vence en los próximos 30 días. Todo en orden.");
    unmount();

    render(<AvisosTabla params={SOL} certificados={null} sol={pagina([])} />);
    expect(screen.getByTestId("avisos-vacio").textContent).toBe("Ninguna empresa tiene envíos atascados por sus credenciales SOL.");
  });

  it("con filas no muestra el estado vacío", () => {
    render(<AvisosTabla params={CERTIFICADOS} certificados={pagina([certificado()])} sol={null} />);

    expect(screen.queryByTestId("avisos-vacio")).toBeNull();
  });

  it("el pie dice qué filas se ven del total y la paginación conserva la vista", () => {
    render(<AvisosTabla params={{ vista: "CREDENCIALES_SOL", pagina: 2, porPagina: 10 }} certificados={null} sol={pagina([sol(), sol({ empresa_id: "b" })], 42)} />);

    expect(screen.getByText("11–12")).toBeTruthy();
    expect(screen.getByText("42")).toBeTruthy();
    const hrefs = screen.getAllByRole("link").map((a) => a.getAttribute("href"));
    expect(hrefs).toContain("/admin/avisos?vista=CREDENCIALES_SOL&pagina=3");
    expect(hrefs, "42 filas de a 10 son cinco páginas, no cuatro").toContain("/admin/avisos?vista=CREDENCIALES_SOL&pagina=5");
    expect(hrefs.some((h) => h?.includes("pagina=6"))).toBe(false);
  });

  it("un clic en una página navega sin recargar", () => {
    render(<AvisosTabla params={{ ...CERTIFICADOS, pagina: 2 }} certificados={pagina([certificado()], 42)} sol={null} />);

    fireEvent.click(screen.getAllByRole("link").find((a) => a.getAttribute("href")?.includes("pagina=3")) as HTMLElement);

    expect(push).toHaveBeenCalledWith("/admin/avisos?pagina=3");
  });

  it("el cambio de filas por página vuelve a la primera página y conserva la vista", () => {
    render(<AvisosTabla params={{ vista: "CREDENCIALES_SOL", pagina: 4, porPagina: 10 }} certificados={null} sol={pagina([sol()], 90)} />);

    fireEvent.click(within(screen.getByRole("group", { name: "Filas por página" })).getByRole("button", { name: "50" }));

    expect(push).toHaveBeenCalledWith("/admin/avisos?vista=CREDENCIALES_SOL&por_pagina=50");
  });
});
