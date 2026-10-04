import { act, cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { EstadoDeServicio, Franja, MonitorDeEmision } from "@/lib/api/admin-monitor";
import type { ApiEnvelope } from "@/lib/api/types";
import { PanelDelMonitor } from "./panel-del-monitor";

const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/browser", () => ({ apiRequest }));

beforeEach(() => vi.useFakeTimers());

afterEach(() => {
  cleanup();
  apiRequest.mockReset();
  vi.useRealTimers();
});

const exito = (datos: MonitorDeEmision): ApiEnvelope<MonitorDeEmision> => ({ estado: "exito", datos, mensaje: null, codigo: null, errores: null });
const fallo = (codigo: string | null, mensaje: string | null): ApiEnvelope<MonitorDeEmision> => ({ estado: "error", datos: null, mensaje, codigo, errores: null });

const franja = (desde: string, p: Partial<Franja> = {}): Franja => {
  const f = { aceptados: 0, rechazados: 0, con_error: 0, en_camino: 0, otros: 0, ...p };
  return { desde, total: f.aceptados + f.rechazados + f.con_error + f.en_camino + f.otros, ...f };
};

/** 24 horas que terminan a las 15:00 UTC (10:00 en Lima), con lo que se pase en la última. */
function horas(ultima: Partial<Franja> = {}, resto: Partial<Franja> = {}): Franja[] {
  const base = Date.parse("2026-10-14T16:00:00Z");
  return Array.from({ length: 24 }, (_, i) => franja(new Date(base + i * 3_600_000).toISOString(), i === 23 ? ultima : resto));
}

const SERVICIOS: EstadoDeServicio[] = [
  { servicio: "ENVIO_PRODUCCION", disponible: true, milisegundos: 140 },
  { servicio: "ENVIO_BETA", disponible: true, milisegundos: 90 },
  { servicio: "CONSULTA_DE_CDR", disponible: false, detalle: "HTTP 503" },
  { servicio: "CONSULTA_DE_VALIDEZ", disponible: false, detalle: "Sin conexión" },
];

function monitor(p: Partial<MonitorDeEmision> = {}): MonitorDeEmision {
  return {
    generado_en: "2026-10-15T15:20:35Z",
    horas: horas({ aceptados: 97, rechazados: 3, con_error: 2, en_camino: 10, otros: 5 }),
    hoy: franja("2026-10-15T05:00:00Z", { aceptados: 400, rechazados: 12, con_error: 4, en_camino: 10, otros: 9, tasa_de_rechazo: 12 / 412 }),
    outbox: { pendientes: 0, vencidos: 0, alerta: false },
    sunat: SERVICIOS,
    ...p,
  };
}

const texto = (id: string) => screen.getByTestId(id).textContent;

/** Deja pasar `ms` del reloj falso y que se resuelvan las promesas que eso dispare. */
const pasar = (ms: number) => act(async () => { await vi.advanceTimersByTimeAsync(ms); });

describe("PanelDelMonitor (#195)", () => {
  // --- la lectura inicial -----------------------------------------------------------------------------------------------------------------

  it("muestra la lectura que trajo el servidor sin pedir nada todavía, con la hora de Lima de la última lectura", () => {
    render(<PanelDelMonitor inicial={monitor()} />);

    expect(texto("monitor-ultima-lectura")).toBe("Última lectura: 10:20:35");
    expect(apiRequest).not.toHaveBeenCalled();
    expect(screen.queryByTestId("monitor-error")).toBeNull();
  });

  it("el día muestra el total y cada categoría con su número", () => {
    render(<PanelDelMonitor inicial={monitor()} />);

    expect(texto("monitor-hoy-total")).toBe("435");
    expect(texto("monitor-hoy-aceptados")).toBe("400");
    expect(texto("monitor-hoy-rechazados")).toBe("12");
    expect(texto("monitor-hoy-con_error")).toBe("4");
    expect(texto("monitor-hoy-en_camino")).toBe("10");
    expect(texto("monitor-hoy-otros")).toBe("9");
  });

  it("la tasa de rechazo es un porcentaje, y sin resueltos es un guion y no 0 %", () => {
    render(<PanelDelMonitor inicial={monitor()} />);
    expect(texto("monitor-tasa")).toBe("2.9 %");
    cleanup();

    render(<PanelDelMonitor inicial={monitor({ hoy: franja("2026-10-15T05:00:00Z", { en_camino: 3 }) })} />);
    expect(texto("monitor-tasa")).toBe("—");
  });

  // --- las horas --------------------------------------------------------------------------------------------------------------------------

  it("dibuja una barra por hora, de la más vieja a la actual, con su total", () => {
    render(<PanelDelMonitor inicial={monitor()} />);

    const barras = screen.getAllByTestId("monitor-hora");
    expect(barras).toHaveLength(24);
    expect(barras[0].getAttribute("data-desde")).toBe("2026-10-14T16:00:00.000Z");
    expect(barras[23].getAttribute("data-desde")).toBe("2026-10-15T15:00:00.000Z");
    expect(barras[23].getAttribute("data-total")).toBe("117");
    expect(barras[0].getAttribute("data-total")).toBe("0");
  });

  it("la barra más alta ocupa todo el alto y las demás, su fracción", () => {
    render(<PanelDelMonitor inicial={monitor({ horas: horas({ aceptados: 100 }, { aceptados: 25 }) })} />);

    const alto = (i: number) => (screen.getAllByTestId("monitor-hora")[i].children[0] as HTMLElement).style.height;
    expect(alto(23)).toBe("100%");
    expect(alto(0)).toBe("25%");
  });

  it("la barra se parte por categorías y una categoría en cero no ocupa lugar", () => {
    render(<PanelDelMonitor inicial={monitor({ horas: horas({ aceptados: 75, rechazados: 25 }) })} />);

    const segmentos = Array.from(screen.getAllByTestId("monitor-hora")[23].children[0].children) as HTMLElement[];
    expect(segmentos.map((s) => s.style.height)).toEqual(["75%", "25%"]);
  });

  it("el eje rotula una de cada tres barras con su hora de Lima y deja las demás sin rótulo", () => {
    render(<PanelDelMonitor inicial={monitor()} />);

    const rotulos = screen.getAllByTestId("monitor-hora").map((b) => b.lastElementChild?.textContent);
    expect(rotulos).toEqual([
      "11:00", "", "", "14:00", "", "", "17:00", "", "", "20:00", "", "",
      "23:00", "", "", "02:00", "", "", "05:00", "", "", "08:00", "", "",
    ]);
  });

  it("cada barra dice su hora de Lima y su total para quien pasa el mouse o usa un lector de pantalla", () => {
    render(<PanelDelMonitor inicial={monitor()} />);

    expect(screen.getAllByTestId("monitor-hora")[23].getAttribute("title")).toBe("10:00: 117 comprobantes");
  });

  it("la tabla por hora trae lo mismo que el gráfico, en hora de Lima", () => {
    render(<PanelDelMonitor inicial={monitor()} />);

    const filas = within(screen.getByText("Ver los números por hora").closest("details") as HTMLElement).getAllByRole("row");
    expect(filas).toHaveLength(25);
    const ultima = within(filas[24]).getAllByRole("cell").map((c) => c.textContent);
    expect(ultima).toEqual(["10:00", "117", "97", "10", "5", "2", "3"]);
  });

  it("sin comprobantes en 24 horas dice eso en lugar de dibujar 24 barras vacías", () => {
    render(<PanelDelMonitor inicial={monitor({ horas: horas() })} />);

    expect(screen.getByTestId("monitor-horas-vacio")).toBeTruthy();
    expect(screen.queryByTestId("monitor-horas")).toBeNull();
  });

  // --- la cola ----------------------------------------------------------------------------------------------------------------------------

  it("una cola vacía lo dice y no da alerta", () => {
    render(<PanelDelMonitor inicial={monitor()} />);

    expect(texto("monitor-outbox-pendientes")).toBe("0");
    expect(texto("monitor-outbox-vencidos")).toBe("0");
    expect(screen.getByTestId("monitor-outbox-vacia")).toBeTruthy();
    expect(screen.queryByTestId("monitor-alerta")).toBeNull();
  });

  it("una cola con pendientes dice desde cuándo está el más antiguo, en hora de Lima", () => {
    render(<PanelDelMonitor inicial={monitor({ outbox: { pendientes: 12, vencidos: 0, mas_viejo_desde: "2026-10-15T12:00:00Z", alerta: false } })} />);

    expect(texto("monitor-outbox-pendientes")).toBe("12");
    expect(texto("monitor-outbox-antiguo")).toContain("15 Oct 2026, 07:00");
    expect(screen.queryByTestId("monitor-outbox-vacia")).toBeNull();
    expect(screen.queryByTestId("monitor-outbox-vencido-hace")).toBeNull();
  });

  it("los envíos vencidos dicen cuánto lleva esperando el que más espera", () => {
    render(<PanelDelMonitor inicial={monitor({ outbox: { pendientes: 3, vencidos: 1, mas_viejo_desde: "2026-10-15T12:00:00Z", vencido_hace_segundos: 120, alerta: false } })} />);

    expect(texto("monitor-outbox-vencido-hace")).toBe("El que más espera lleva 2 min.");
    expect(screen.queryByTestId("monitor-alerta")).toBeNull();
  });

  it("la alerta aparece solo cuando el backend la da y dice cuántos y desde hace cuánto", () => {
    render(<PanelDelMonitor inicial={monitor({ outbox: { pendientes: 5, vencidos: 3, mas_viejo_desde: "2026-10-15T12:00:00Z", vencido_hace_segundos: 3900, alerta: true } })} />);

    const alerta = screen.getByTestId("monitor-alerta");
    expect(alerta.getAttribute("role")).toBe("alert");
    expect(alerta.textContent).toContain("El envío a SUNAT parece detenido");
    expect(alerta.textContent).toContain("Hay 3 envíos vencidos y el que más espera lleva 1 h 5 min.");
  });

  it("la alerta de un solo envío está en singular", () => {
    render(<PanelDelMonitor inicial={monitor({ outbox: { pendientes: 1, vencidos: 1, vencido_hace_segundos: 400, alerta: true } })} />);

    expect(screen.getByTestId("monitor-alerta").textContent).toContain("Hay 1 envío vencido y lleva 6 min esperando.");
  });

  // --- SUNAT ------------------------------------------------------------------------------------------------------------------------------

  it("cada servicio de SUNAT dice si contesta, cuánto tardó o por qué no", () => {
    render(<PanelDelMonitor inicial={monitor()} />);

    const filas = screen.getAllByTestId("monitor-servicio");
    expect(filas.map((f) => f.getAttribute("data-servicio"))).toEqual(["ENVIO_PRODUCCION", "ENVIO_BETA", "CONSULTA_DE_CDR", "CONSULTA_DE_VALIDEZ"]);
    expect(filas.map((f) => f.getAttribute("data-disponible"))).toEqual(["true", "true", "false", "false"]);
    expect(filas[0].textContent).toContain("Envío (producción)");
    expect(filas[0].textContent).toContain("Disponible");
    expect(filas[0].textContent).toContain("140 ms");
    expect(filas[2].textContent).toContain("Consulta de CDR");
    expect(filas[2].textContent).toContain("No disponible");
    expect(filas[2].textContent).toContain("HTTP 503");
    expect(filas[3].textContent).toContain("Sin conexión");
  });

  it("el estado de cada servicio va en verde si contesta y en rojo si no", () => {
    render(<PanelDelMonitor inicial={monitor()} />);

    const filas = screen.getAllByTestId("monitor-servicio");
    expect(within(filas[0]).getByText("Disponible").className).toContain("bg-success");
    expect(within(filas[0]).getByText("Disponible").className).not.toContain("bg-destructive");
    expect(within(filas[2]).getByText("No disponible").className).toContain("bg-destructive");
    expect(within(filas[2]).getByText("No disponible").className).not.toContain("bg-success");
  });

  it("sin servicios configurados lo dice", () => {
    render(<PanelDelMonitor inicial={monitor({ sunat: [] })} />);

    expect(screen.getByTestId("monitor-sunat-vacio")).toBeTruthy();
    expect(screen.queryAllByTestId("monitor-servicio")).toHaveLength(0);
  });

  // --- el refresco ------------------------------------------------------------------------------------------------------------------------

  it("pide una lectura nueva cada 30 segundos por el BFF y la muestra", async () => {
    apiRequest.mockResolvedValue(exito(monitor({ generado_en: "2026-10-15T15:21:05Z", outbox: { pendientes: 7, vencidos: 0, alerta: false } })));
    render(<PanelDelMonitor inicial={monitor()} />);

    await pasar(29_999);
    expect(apiRequest).not.toHaveBeenCalled();
    await pasar(1);

    expect(apiRequest).toHaveBeenCalledTimes(1);
    expect(apiRequest).toHaveBeenCalledWith("/api/admin/monitor", { method: "GET" });
    expect(texto("monitor-ultima-lectura")).toBe("Última lectura: 10:21:05");
    expect(texto("monitor-outbox-pendientes")).toBe("7");
  });

  it("sigue pidiendo en cada intervalo", async () => {
    apiRequest.mockResolvedValue(exito(monitor()));
    render(<PanelDelMonitor inicial={monitor()} />);

    await pasar(90_000);

    expect(apiRequest).toHaveBeenCalledTimes(3);
  });

  it("respeta el intervalo que se le dé", async () => {
    apiRequest.mockResolvedValue(exito(monitor()));
    render(<PanelDelMonitor inicial={monitor()} intervaloMs={5_000} />);

    await pasar(5_000);

    expect(apiRequest).toHaveBeenCalledTimes(1);
  });

  it("no encima pedidos: si el anterior no terminó, el siguiente tick no pide otro", async () => {
    let terminar: (r: ApiEnvelope<MonitorDeEmision>) => void = () => {};
    apiRequest.mockReturnValue(new Promise((resolver) => { terminar = resolver; }));
    render(<PanelDelMonitor inicial={monitor()} intervaloMs={1_000} />);

    await pasar(3_500);
    expect(apiRequest).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("monitor-actualizando")).toBeTruthy();

    terminar(exito(monitor()));
    await pasar(0);
    expect(screen.queryByTestId("monitor-actualizando")).toBeNull();
    await pasar(1_000);
    expect(apiRequest).toHaveBeenCalledTimes(2);
  });

  it("una lectura que falla deja la última en pantalla, lo dice y se recupera sola con la siguiente", async () => {
    apiRequest.mockResolvedValueOnce(fallo("RED", "No se pudo conectar con el servidor.")).mockResolvedValue(exito(monitor({ generado_en: "2026-10-15T15:22:00Z" })));
    render(<PanelDelMonitor inicial={monitor()} />);

    await pasar(30_000);

    expect(screen.getByTestId("monitor-error").textContent).toContain("No se pudo actualizar el monitor. Se muestra la última lectura.");
    expect(screen.getByTestId("monitor-error").textContent).toContain("No se pudo conectar con el servidor.");
    expect(texto("monitor-ultima-lectura")).toBe("Última lectura: 10:20:35");
    expect(texto("monitor-hoy-total")).toBe("435");

    await pasar(30_000);

    expect(screen.queryByTestId("monitor-error")).toBeNull();
    expect(texto("monitor-ultima-lectura")).toBe("Última lectura: 10:22:00");
  });

  it("el botón de reintentar pide la lectura sin esperar al intervalo", async () => {
    apiRequest.mockResolvedValueOnce(fallo("RED", "sin red")).mockResolvedValue(exito(monitor({ generado_en: "2026-10-15T15:23:00Z" })));
    render(<PanelDelMonitor inicial={monitor()} />);
    await pasar(30_000);

    await act(async () => { fireEvent.click(screen.getByRole("button", { name: "Reintentar" })); });

    expect(apiRequest).toHaveBeenCalledTimes(2);
    expect(screen.queryByTestId("monitor-error")).toBeNull();
  });

  it("si la sesión terminó lo dice, deja de pedir y no ofrece reintentar", async () => {
    apiRequest.mockResolvedValue(fallo("NO_AUTORIZADO", "Sesión de administrador requerida"));
    render(<PanelDelMonitor inicial={monitor()} />);

    await pasar(30_000);
    expect(screen.getByTestId("monitor-error").textContent).toContain("Tu sesión terminó. Recarga la página para volver a entrar.");
    expect(screen.queryByRole("button", { name: "Reintentar" })).toBeNull();

    await pasar(120_000);
    expect(apiRequest).toHaveBeenCalledTimes(1);
  });

  it("una respuesta de éxito sin datos cuenta como fallo y no borra la lectura", async () => {
    apiRequest.mockResolvedValue({ estado: "exito", datos: null, mensaje: null, codigo: null, errores: null });
    render(<PanelDelMonitor inicial={monitor()} />);

    await pasar(30_000);

    expect(screen.getByTestId("monitor-error")).toBeTruthy();
    expect(texto("monitor-hoy-total")).toBe("435");
  });

  it("al desmontar deja de pedir", async () => {
    apiRequest.mockResolvedValue(exito(monitor()));
    const { unmount } = render(<PanelDelMonitor inicial={monitor()} />);
    unmount();

    await pasar(120_000);

    expect(apiRequest).not.toHaveBeenCalled();
  });

  // --- sin lectura inicial ----------------------------------------------------------------------------------------------------------------

  it("sin lectura inicial la pide enseguida y la muestra", async () => {
    apiRequest.mockResolvedValue(exito(monitor()));
    render(<PanelDelMonitor inicial={null} />);

    expect(screen.queryByTestId("monitor-ultima-lectura")).toBeNull();
    await pasar(0);

    expect(apiRequest).toHaveBeenCalledTimes(1);
    expect(texto("monitor-hoy-total")).toBe("435");
  });

  it("si tampoco llega la primera lectura dice que no se pudo cargar y deja reintentar", async () => {
    apiRequest.mockResolvedValueOnce(fallo("RED", "sin red")).mockResolvedValue(exito(monitor()));
    render(<PanelDelMonitor inicial={null} />);
    await pasar(0);

    expect(screen.getByTestId("monitor-error").textContent).toContain("No se pudo cargar el monitor.");
    expect(screen.queryByTestId("monitor-hoy-total")).toBeNull();

    await act(async () => { fireEvent.click(screen.getByRole("button", { name: "Reintentar" })); });

    expect(screen.queryByTestId("monitor-error")).toBeNull();
    expect(texto("monitor-hoy-total")).toBe("435");
  });

  it("usa el mensaje del backend si lo hay y uno genérico si no", async () => {
    apiRequest.mockResolvedValue(fallo("ERROR_RARO", null));
    render(<PanelDelMonitor inicial={null} />);
    await pasar(0);

    expect(screen.getByTestId("monitor-error").textContent).toContain("No se pudo cargar el monitor.");
    expect((screen.getByTestId("monitor-error").textContent ?? "").length).toBeGreaterThan("No se pudo cargar el monitor.Reintentar".length);
  });
});
