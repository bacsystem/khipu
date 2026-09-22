import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { NuevoComprobanteDialog } from "./nuevo-comprobante-dialog";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));

const SERIES = [{ tipo: "01", serie: "F001", ultimo_numero: 2, activa: true, establecimiento: "0000" }];

function sobre(datos: unknown, status = 200) {
  return new Response(JSON.stringify({ estado: status < 400 ? "exito" : "error", datos, mensaje: null, codigo: null, errores: null }), { status });
}

/** Respondedor por ruta: cada test define qué devuelve `/series` y `/empresa` en cada llamada. */
function stubFetch(porRuta: (url: string) => Response) {
  const fetch = vi.fn((url: string) => Promise.resolve(porRuta(url)));
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

function llamadasA(fetch: ReturnType<typeof stubFetch>, ruta: string) {
  return fetch.mock.calls.filter(([url]) => String(url).includes(ruta)).length;
}

async function abrir() {
  fireEvent.click(screen.getByRole("button", { name: /nuevo comprobante/i }));
  await waitFor(() => expect(screen.getByLabelText("Serie")).toBeInTheDocument());
}

afterEach(() => {
  // La config de vitest no usa `globals`, así que testing-library no registra su limpieza automática: sin esto
  // el diálogo de un test sigue montado en el siguiente y las consultas encuentran elementos duplicados.
  cleanup();
  vi.unstubAllGlobals();
});

describe("NuevoComprobanteDialog", () => {
  it("un fallo al leer las series no se muestra como 'no tienes series' y se puede reintentar", async () => {
    let fallar = true;
    stubFetch((url) => {
      if (url.includes("/series")) return fallar ? sobre(null, 502) : sobre(SERIES);
      return sobre({ id: "e-1", entorno: "BETA" });
    });

    render(<NuevoComprobanteDialog />);
    fireEvent.click(screen.getByRole("button", { name: /nuevo comprobante/i }));

    // Sin esta distinción, un 502 pasajero deja `series` en `[]` y el diálogo manda al usuario a crear series
    // que ya tiene —y el estado sobrevive a cerrar y reabrir, así que la emisión queda muerta hasta recargar.
    await waitFor(() => expect(screen.getByText("No se pudieron cargar tus series")).toBeInTheDocument());
    expect(screen.queryByText("No tienes series de factura")).not.toBeInTheDocument();

    fallar = false;
    fireEvent.click(screen.getByRole("button", { name: "Reintentar" }));

    await waitFor(() => expect(screen.getByLabelText("Serie")).toBeInTheDocument());
    expect(screen.queryByText("No se pudieron cargar tus series")).not.toBeInTheDocument();
  });

  it("una lista vacía sí es 'no tienes series': son dos situaciones distintas", async () => {
    stubFetch((url) => (url.includes("/series") ? sobre([]) : sobre({ id: "e-1", entorno: "BETA" })));

    render(<NuevoComprobanteDialog />);
    fireEvent.click(screen.getByRole("button", { name: /nuevo comprobante/i }));

    await waitFor(() => expect(screen.getByText("No tienes series de factura")).toBeInTheDocument());
  });

  it("no afirma el ambiente si no se pudo leer la empresa, y avisa que la tasa previsualizada puede no ser la suya", async () => {
    stubFetch((url) => (url.includes("/series") ? sobre(SERIES) : sobre(null, 500)));

    render(<NuevoComprobanteDialog />);
    fireEvent.click(screen.getByRole("button", { name: /nuevo comprobante/i }));

    // Decir "Homologación" cuando el tenant está en producción invita a emitir de verdad creyendo que es una prueba.
    await waitFor(() => expect(screen.getByText("No se pudo leer la configuración de la empresa")).toBeInTheDocument());
    expect(screen.queryByText(/Homologación/)).not.toBeInTheDocument();
    // Sin empresa la previsualización cae al 18 %: un tenant del padrón (10.5 %) vería totales que no son los que
    // va a emitir, así que el aviso tiene que nombrar la tasa, no solo el ambiente.
    expect(screen.getByText(/los totales se previsualizan con IGV 18 %/)).toBeInTheDocument();
    // El formulario sigue disponible: la empresa no bloquea la emisión.
    expect(screen.getByLabelText("Serie")).toBeInTheDocument();
  });

  it("la empresa se reintenta sola, sin arrastrar a las series ni volver a pedirlas", async () => {
    let fallarEmpresa = true;
    const fetch = stubFetch((url) => {
      if (url.includes("/series")) return sobre(SERIES);
      return fallarEmpresa ? sobre(null, 500) : sobre({ id: "e-1", entorno: "PRODUCCION", padron_tasa_especial_igv: true });
    });

    render(<NuevoComprobanteDialog />);
    fireEvent.click(screen.getByRole("button", { name: /nuevo comprobante/i }));
    await waitFor(() => expect(screen.getByText("No se pudo leer la configuración de la empresa")).toBeInTheDocument());

    const seriesAntes = llamadasA(fetch, "/series");
    fallarEmpresa = false;
    fireEvent.click(screen.getByRole("button", { name: "Reintentar" }));

    // Al llegar la empresa, el ambiente se afirma y la tasa pasa a ser la del padrón.
    await waitFor(() => expect(screen.getByText(/Producción/)).toBeInTheDocument());
    expect(screen.getByText("IGV (10.5 %)")).toBeInTheDocument();
    // Cada recurso tiene su efecto: reintentar la empresa no vuelve a pedir las series, que ya estaban cargadas.
    expect(llamadasA(fetch, "/series")).toBe(seriesAntes);
  });

  it("al reabrir recarga las series: cachearlas anunciaría el correlativo que ya consumió la emisión anterior", async () => {
    const fetch = stubFetch((url) => (url.includes("/series") ? sobre(SERIES) : sobre({ id: "e-1", entorno: "BETA" })));

    render(<NuevoComprobanteDialog />);
    await abrir();
    const primeraApertura = llamadasA(fetch, "/series");

    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));
    await waitFor(() => expect(screen.queryByLabelText("Serie")).not.toBeInTheDocument());

    // El diálogo vive en el top bar y sobrevive a la navegación posterior a emitir, así que si el estado no se
    // olvidara al cerrar, `ultimo_numero` seguiría siendo el de antes de la emisión.
    await abrir();
    expect(llamadasA(fetch, "/series")).toBe(primeraApertura + 1);
  });
});
