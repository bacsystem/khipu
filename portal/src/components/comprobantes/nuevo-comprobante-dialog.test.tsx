import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { pedirDuplicado } from "@/lib/comprobantes/duplicar";
import { NuevoComprobanteDialog } from "./nuevo-comprobante-dialog";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));

/** Una empresa que puede emitir: con certificado y credenciales SOL (C2). */
const LISTA_PARA_EMITIR = { tiene_certificado: true, certificado_vigencia_hasta: null, tiene_credenciales_sol: true };

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
      return sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "BETA" });
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
    stubFetch((url) => (url.includes("/series") ? sobre([]) : sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "BETA" })));

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
      return fallarEmpresa ? sobre(null, 500) : sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "PRODUCCION", padron_tasa_especial_igv: true });
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
    const fetch = stubFetch((url) => (url.includes("/series") ? sobre(SERIES) : sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "BETA" })));

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

  /**
   * 264-H1: las series y la empresa se piden a la vez. Si las series llegaban primero, el formulario se montaba, el usuario empezaba a llenarlo, y al
   * llegar una empresa sin certificado se desmontaba con lo escrito. Hasta saber si la empresa puede emitir, no hay formulario.
   */
  it("no muestra el formulario hasta saber si la empresa puede emitir", async () => {
    let soltarEmpresa: (r: Response) => void = () => {};
    const empresaPendiente = new Promise<Response>((r) => (soltarEmpresa = r));
    vi.stubGlobal("fetch", vi.fn((url: string) => (String(url).includes("/series") ? Promise.resolve(sobre(SERIES)) : empresaPendiente)));

    render(<NuevoComprobanteDialog />);
    fireEvent.click(screen.getByRole("button", { name: /nuevo comprobante/i }));
    await waitFor(() => expect(screen.getByText("Cargando…")).toBeInTheDocument());
    expect(screen.queryByLabelText("Serie")).not.toBeInTheDocument();

    soltarEmpresa(sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "BETA" }));
    await waitFor(() => expect(screen.getByLabelText("Serie")).toBeInTheDocument());
  });

  /** C2: el backend rechazaría la emisión; el diálogo lo dice antes de que se llene nada y no muestra el formulario. */
  it("sin certificado vigente ni credenciales SOL dice qué falta en vez de mostrar el formulario", async () => {
    stubFetch((url) =>
      url.includes("/series")
        ? sobre(SERIES)
        : sobre({ id: "e-1", entorno: "BETA", tiene_certificado: true, certificado_vigencia_hasta: "2020-01-01", tiene_credenciales_sol: false }),
    );

    render(<NuevoComprobanteDialog />);
    fireEvent.click(screen.getByRole("button", { name: /nuevo comprobante/i }));

    await waitFor(() => expect(screen.getByText("Esta empresa todavía no puede emitir")).toBeInTheDocument());
    expect(screen.getByText("Renovar el certificado digital: el cargado ya venció.")).toBeInTheDocument();
    expect(screen.getByText("Guardar el usuario SOL secundario y su clave.")).toBeInTheDocument();
    expect(screen.queryByLabelText("Serie")).not.toBeInTheDocument();
    // #276: abre la pestaña de lo primero que falta (el certificado), no la página en la pestaña que sea.
    expect(screen.getByRole("link", { name: "Ir a Fiscal & certificado" })).toHaveAttribute("href", "/empresa?seccion=certificado");
  });

  it("si solo faltan las credenciales SOL, el enlace abre esa pestaña", async () => {
    stubFetch((url) => (url.includes("/series") ? sobre(SERIES) : sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "BETA", tiene_credenciales_sol: false })));

    render(<NuevoComprobanteDialog />);
    fireEvent.click(screen.getByRole("button", { name: /nuevo comprobante/i }));

    await waitFor(() => expect(screen.getByRole("link", { name: "Ir a Fiscal & certificado" })).toHaveAttribute("href", "/empresa?seccion=sol"));
  });

  /** #18: en el tope del plan el backend rechaza la emisión (429 LIMITE_PLAN); el diálogo lo dice antes de que se llene nada. */
  it("con la cuenta en el tope de documentos del mes no muestra el formulario y lleva a Plan y consumo", async () => {
    stubFetch((url) =>
      url.includes("/series")
        ? sobre(SERIES)
        : url.includes("/cuenta")
          ? sobre({ nombre: "Mi negocio", plan: { plan: { nombre: "Gratis" }, estado: "VIGENTE" }, consumo: { mes: "2026-10", documentos: 30, maximo: 30 } })
          : sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "BETA" }),
    );

    render(<NuevoComprobanteDialog />);
    fireEvent.click(screen.getByRole("button", { name: /nuevo comprobante/i }));

    await waitFor(() => expect(screen.getByText("Llegaste al tope de documentos del mes")).toBeInTheDocument());
    expect(screen.getByText(/Tu plan Gratis permite 30 documentos al mes y ya los usaste/)).toBeInTheDocument();
    expect(screen.queryByLabelText("Serie")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ver plan y consumo" })).toHaveAttribute("href", "/cuenta/plan");
  });

  /** #285: con el plan vencido (pasada la gracia) el backend rechaza la emisión (402 PLAN_VENCIDO); el diálogo lo dice antes, aunque quede lugar en el tope. */
  it("con el plan vencido no muestra el formulario y lleva a Plan y consumo", async () => {
    stubFetch((url) =>
      url.includes("/series")
        ? sobre(SERIES)
        : url.includes("/cuenta")
          ? sobre({ nombre: "Mi negocio", plan: { plan: { nombre: "Emprende" }, estado: "VENCIDA" }, consumo: { mes: "2026-10", documentos: 3, maximo: 300 } })
          : sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "BETA" }),
    );

    render(<NuevoComprobanteDialog />);
    fireEvent.click(screen.getByRole("button", { name: /nuevo comprobante/i }));

    await waitFor(() => expect(screen.getByText("Tu plan venció")).toBeInTheDocument());
    expect(screen.getByText(/Tu plan Emprende venció y terminaron sus días de gracia/)).toBeInTheDocument();
    expect(screen.queryByText("Llegaste al tope de documentos del mes")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Serie")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ver plan y consumo" })).toHaveAttribute("href", "/cuenta/plan");
  });

  /** 345-H2: vencido y en el tope a la vez, manda el vencimiento: el aviso del tope diría que vuelve a emitir el mes que viene, y no es así. */
  it("vencido y en el tope dice que el plan venció, no que llegó al tope", async () => {
    stubFetch((url) =>
      url.includes("/series")
        ? sobre(SERIES)
        : url.includes("/cuenta")
          ? sobre({ nombre: "Mi negocio", plan: { plan: { nombre: "Emprende" }, estado: "VENCIDA" }, consumo: { mes: "2026-10", documentos: 300, maximo: 300 } })
          : sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "BETA" }),
    );

    render(<NuevoComprobanteDialog />);
    fireEvent.click(screen.getByRole("button", { name: /nuevo comprobante/i }));

    await waitFor(() => expect(screen.getByText("Tu plan venció")).toBeInTheDocument());
    expect(screen.queryByText("Llegaste al tope de documentos del mes")).not.toBeInTheDocument();
  });

  /** #285: en gracia se sigue emitiendo. */
  it("con el plan en gracia deja emitir", async () => {
    stubFetch((url) =>
      url.includes("/series")
        ? sobre(SERIES)
        : url.includes("/cuenta")
          ? sobre({ nombre: "Mi negocio", plan: { plan: { nombre: "Emprende" }, estado: "EN_GRACIA" }, consumo: { mes: "2026-10", documentos: 3, maximo: 300 } })
          : sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "BETA" }),
    );

    render(<NuevoComprobanteDialog />);
    await abrir();

    expect(screen.queryByText("Tu plan venció")).not.toBeInTheDocument();
  });

  it("con lugar en el plan, o sin poder leer la cuenta, deja emitir: el backend tiene la última palabra", async () => {
    stubFetch((url) =>
      url.includes("/series")
        ? sobre(SERIES)
        : url.includes("/cuenta")
          ? sobre(null, 502)
          : sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "BETA" }),
    );

    render(<NuevoComprobanteDialog />);
    await abrir();

    expect(screen.queryByText("Llegaste al tope de documentos del mes")).not.toBeInTheDocument();
  });

  /** #107: con las credenciales rechazadas se puede emitir, pero el envío espera; se dice antes de emitir, no después. */
  it("con las credenciales SOL rechazadas deja emitir y avisa que el envío espera a corregirlas", async () => {
    stubFetch((url) =>
      url.includes("/series")
        ? sobre(SERIES)
        : sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "BETA", credenciales_sol_rechazadas: { desde: "2026-10-09T15:00:00Z", motivo: "0102 - Usuario o contrasena incorrectos" } }),
    );

    render(<NuevoComprobanteDialog />);
    await abrir();

    await waitFor(() => expect(screen.getByText("SUNAT rechazó las credenciales SOL de esta empresa")).toBeInTheDocument());
    expect(screen.getByRole("link", { name: "Corregirlas" })).toHaveAttribute("href", "/empresa?seccion=sol");
    expect(screen.getByLabelText("Serie")).toBeInTheDocument();
  });

  /** #20: la boleta se elige arriba, usa solo series B### y pide el documento del comprador, no un RUC. */
  it("eligiendo Boleta muestra sus series y pide el documento del comprador", async () => {
    const conBoleta = [...SERIES, { tipo: "03", serie: "B001", ultimo_numero: 9, activa: true, establecimiento: "0000" }];
    stubFetch((url) => (url.includes("/series") ? sobre(conBoleta) : sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "BETA" })));

    render(<NuevoComprobanteDialog />);
    await abrir();
    expect(screen.getByRole("option", { name: /F001/ })).toBeInTheDocument();
    expect(screen.getByLabelText("RUC")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Boleta" }));

    await waitFor(() => expect(screen.getByRole("option", { name: /B001 · siguiente N\.º 10/ })).toBeInTheDocument());
    expect(screen.queryByRole("option", { name: /F001/ })).not.toBeInTheDocument();
    expect(screen.getByLabelText("Documento del comprador")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Emitir boleta" })).toBeInTheDocument();
  });

  /** #286: una empresa que solo emite boletas no tiene que pasar por «No tienes series de factura» cada vez que abre el diálogo. */
  it("con solo series de boleta abre en Boleta", async () => {
    const soloBoleta = [{ tipo: "03", serie: "B001", ultimo_numero: 9, activa: true, establecimiento: "0000" }];
    stubFetch((url) => (url.includes("/series") ? sobre(soloBoleta) : sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "BETA" })));

    render(<NuevoComprobanteDialog />);
    await abrir();

    expect(screen.getByRole("button", { name: "Boleta" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("option", { name: /B001 · siguiente N\.º 10/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Emitir boleta" })).toBeInTheDocument();
    expect(screen.queryByText("No tienes series de factura")).not.toBeInTheDocument();
    // La factura sigue a un clic: abrir en Boleta no esconde la otra opción.
    fireEvent.click(screen.getByRole("button", { name: "Factura" }));
    await waitFor(() => expect(screen.getByText("No tienes series de factura")).toBeInTheDocument());
  });

  /** Una serie de factura dada de baja no cuenta: la factura no se puede emitir con ella. */
  it("con la serie de factura inactiva y una de boleta activa abre en Boleta", async () => {
    const series = [{ ...SERIES[0], activa: false }, { tipo: "03", serie: "B001", ultimo_numero: 9, activa: true, establecimiento: "0000" }];
    stubFetch((url) => (url.includes("/series") ? sobre(series) : sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "BETA" })));

    render(<NuevoComprobanteDialog />);
    await abrir();

    expect(screen.getByRole("button", { name: "Emitir boleta" })).toBeInTheDocument();
  });

  it("con series de los dos tipos sigue abriendo en Factura", async () => {
    const ambas = [...SERIES, { tipo: "03", serie: "B001", ultimo_numero: 9, activa: true, establecimiento: "0000" }];
    stubFetch((url) => (url.includes("/series") ? sobre(ambas) : sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "BETA" })));

    render(<NuevoComprobanteDialog />);
    await abrir();

    expect(screen.getByRole("button", { name: "Factura" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Emitir factura" })).toBeInTheDocument();
  });

  /** La elección dura lo que el diálogo: al reabrir vuelve al tipo que la empresa puede emitir, con las series recién leídas (pudo cambiar de empresa). */
  it("al reabrir vuelve al tipo por defecto de las series recién leídas", async () => {
    let series: unknown[] = [...SERIES, { tipo: "03", serie: "B001", ultimo_numero: 9, activa: true, establecimiento: "0000" }];
    stubFetch((url) => (url.includes("/series") ? sobre(series) : sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "BETA" })));

    render(<NuevoComprobanteDialog />);
    await abrir();
    fireEvent.click(screen.getByRole("button", { name: "Boleta" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Emitir boleta" })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));
    await waitFor(() => expect(screen.queryByLabelText("Serie")).not.toBeInTheDocument());

    await abrir();
    expect(screen.getByRole("button", { name: "Emitir factura" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Factura" }));
    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));
    await waitFor(() => expect(screen.queryByLabelText("Serie")).not.toBeInTheDocument());

    // Otra empresa, solo con boletas: lo elegido antes (Factura) no la deja en «No tienes series de factura».
    series = [{ tipo: "03", serie: "B002", ultimo_numero: 0, activa: true, establecimiento: "0000" }];
    await abrir();
    expect(screen.getByRole("button", { name: "Emitir boleta" })).toBeInTheDocument();
  });

  it("sin series de boleta lo dice con su tipo, sin esconder la opción", async () => {
    stubFetch((url) => (url.includes("/series") ? sobre(SERIES) : sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "BETA" })));

    render(<NuevoComprobanteDialog />);
    await abrir();
    fireEvent.click(screen.getByRole("button", { name: "Boleta" }));

    await waitFor(() => expect(screen.getByText("No tienes series de boleta")).toBeInTheDocument());
    expect(screen.getByText(/serie de tipo 03 activa/)).toBeInTheDocument();
  });

  it("sin rechazo no hay aviso de credenciales", async () => {
    stubFetch((url) => (url.includes("/series") ? sobre(SERIES) : sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "BETA" })));

    render(<NuevoComprobanteDialog />);
    await abrir();
    await waitFor(() => expect(screen.getByText(/Homologación/)).toBeInTheDocument());

    expect(screen.queryByText("SUNAT rechazó las credenciales SOL de esta empresa")).not.toBeInTheDocument();
  });
});

describe("NuevoComprobanteDialog — duplicar (#323)", () => {
  const FACTURA = {
    id: "f-1", tipo: "01", serie: "F001", numero: 7, fecha_emision: "2026-09-02", moneda: "USD", tipo_operacion: "0101",
    receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CLIENTE SAC", direccion: "Av. Prueba 123" },
    items: [{ codigo: null, descripcion: "Soporte mensual", unidad: "ZZ", cantidad: 3, precio_unitario: 59, tipo_afectacion_igv: "10" }],
    estado_documento: "ACEPTADO",
  };
  const SERIES_FB = [
    { tipo: "01", serie: "F001", ultimo_numero: 7, activa: true, establecimiento: "0000" },
    { tipo: "03", serie: "B001", ultimo_numero: 2, activa: true, establecimiento: "0000" },
  ];

  it("el pedido de duplicar abre el diálogo con el cliente, la moneda y las líneas de la copia", async () => {
    stubFetch((url) => {
      if (url.includes("/facturas/f-1")) return sobre(FACTURA);
      if (url.includes("/series")) return sobre(SERIES_FB);
      return sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "BETA" });
    });
    render(<NuevoComprobanteDialog />);
    pedirDuplicado("f-1");

    await waitFor(() => expect(screen.getByLabelText("RUC")).toHaveValue("20554198211"));
    expect(screen.getByLabelText("Razón social")).toHaveValue("CLIENTE SAC");
    expect(screen.getByLabelText("Moneda")).toHaveValue("USD");
    expect(screen.getByLabelText("Descripción")).toHaveValue("Soporte mensual");
    expect(screen.getByLabelText("Fecha de emisión")).toHaveValue(new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" }));
  });

  it("duplicar una boleta abre la pestaña Boleta, y abrir a mano después empieza de cero", async () => {
    stubFetch((url) => {
      if (url.includes("/facturas/b-1")) return sobre({ ...FACTURA, id: "b-1", tipo: "03", serie: "B001", receptor: { tipo_doc: "1", num_doc: "44781209", razon_social: "MIGUEL", direccion: null } });
      if (url.includes("/series")) return sobre(SERIES_FB);
      return sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "BETA" });
    });
    render(<NuevoComprobanteDialog />);
    pedirDuplicado("b-1");
    await waitFor(() => expect(screen.getByLabelText("Número de documento")).toHaveValue("44781209"));
    expect(screen.getByRole("button", { name: "Boleta" })).toHaveAttribute("aria-pressed", "true");

    // Cerrar y abrir con el botón es un comprobante nuevo, no la copia.
    fireEvent.keyDown(document.activeElement ?? document.body, { key: "Escape" });
    await waitFor(() => expect(screen.queryByLabelText("Número de documento")).not.toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: /nuevo comprobante/i }));
    await waitFor(() => expect(screen.getByLabelText("Serie")).toBeInTheDocument());
    expect(screen.getByLabelText("RUC")).toHaveValue("");
  });

  it("si no se puede leer el comprobante a copiar, abre el diálogo con un aviso y sin copia", async () => {
    stubFetch((url) => {
      if (url.includes("/facturas/f-9")) return sobre(null, 404);
      if (url.includes("/series")) return sobre(SERIES_FB);
      return sobre({ ...LISTA_PARA_EMITIR, id: "e-1", entorno: "BETA" });
    });
    render(<NuevoComprobanteDialog />);
    pedirDuplicado("f-9");
    expect(await screen.findByText("No se pudo copiar el comprobante")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText("RUC")).toHaveValue(""));
  });
});
