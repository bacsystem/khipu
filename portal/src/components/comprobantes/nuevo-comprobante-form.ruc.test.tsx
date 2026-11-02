import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { NuevoComprobanteForm } from "./nuevo-comprobante-form";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));

const SERIES = [
  { tipo: "01", serie: "F001", ultimo_numero: 2, activa: true, establecimiento: "0000" },
  { tipo: "03", serie: "B001", ultimo_numero: 9, activa: true, establecimiento: "0000" },
];

const TIENDAS = { ruc: "20100066603", razon_social: "TIENDAS DEL SUR S.A.C.", estado: "ACTIVO", condicion: "HABIDO", activo: true, habido: true, ubigeo: "150101", direccion: "AV. REPUBLICA DE PANAMA NRO. 2457", actualizado_al: "2026-10-16T09:30:00Z" };
// Como lo serializa el backend (`non_null`): sin `direccion` ni `ubigeo`, no con `null`.
const TIENDAS_SIN_DOMICILIO: Partial<typeof TIENDAS> = { ...TIENDAS };
delete TIENDAS_SIN_DOMICILIO.direccion;
delete TIENDAS_SIN_DOMICILIO.ubigeo;
const DE_BAJA = { ...TIENDAS_SIN_DOMICILIO, ruc: "20160109441", razon_social: "QUISPE MAMANI JOSE", estado: "BAJA DE OFICIO", condicion: "NO HABIDO", activo: false, habido: false };
const NO_HABIDO = { ...TIENDAS, ruc: "20602160280", razon_social: "COMERCIAL VERDE E.I.R.L.", condicion: "NO HABIDO", habido: false };

type Respuesta = { status: number; cuerpo: unknown };
const exito = (datos: unknown): Respuesta => ({ status: 200, cuerpo: { estado: "exito", datos, mensaje: null, codigo: null, errores: null } });
const falla = (status: number, codigo: string): Respuesta => ({ status, cuerpo: { estado: "error", datos: null, mensaje: "x", codigo, errores: null } });

/** Contesta la consulta de cada RUC con lo indicado y cuenta los pedidos; cualquier otra ruta, vacía. */
function stubPadron(porRuc: Record<string, Respuesta | (() => Respuesta)>) {
  const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>((url) => {
    const ruc = String(url).match(/\/consultas\/ruc\/(\d+)/)?.[1];
    if (!ruc) return Promise.resolve(new Response(JSON.stringify({ estado: "exito", datos: null, mensaje: null, codigo: null, errores: null })));
    const r = porRuc[ruc];
    const { status, cuerpo } = typeof r === "function" ? r() : (r ?? falla(404, "NO_ENCONTRADO"));
    return Promise.resolve(new Response(JSON.stringify(cuerpo), { status }));
  });
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

const consultas = (fetch: ReturnType<typeof stubPadron>) => fetch.mock.calls.filter(([url]) => String(url).includes("/consultas/ruc/")).map(([url]) => String(url));

/** Como un usuario: un dígito a la vez, que es como se dispara la consulta en la vida real. */
function digitar(etiqueta: string | RegExp, valor: string) {
  const campo = screen.getByLabelText(etiqueta);
  for (let i = 1; i <= valor.length; i++) fireEvent.change(campo, { target: { value: valor.slice(0, i) } });
}

const ruc = () => screen.getByLabelText("RUC") as HTMLInputElement;
const razon = () => screen.getByLabelText("Razón social") as HTMLInputElement;
const direccion = () => screen.getByLabelText(/Dirección/) as HTMLInputElement;

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("NuevoComprobanteForm — consulta del RUC en el padrón de SUNAT (#379)", () => {
  it("al completar un RUC válido rellena la razón social y la dirección, con un solo pedido aunque se digite dígito a dígito", async () => {
    const fetch = stubPadron({ "20100066603": exito(TIENDAS) });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);

    digitar("RUC", "20100066603");

    await waitFor(() => expect(razon().value).toBe("TIENDAS DEL SUR S.A.C."));
    expect(direccion().value).toBe("AV. REPUBLICA DE PANAMA NRO. 2457");
    expect(consultas(fetch)).toEqual(["/api/proxy/consultas/ruc/20100066603"]);
    expect(screen.getByTestId("consulta-ruc")).toHaveAttribute("data-estado", "encontrado");
    expect(screen.queryByTestId("aviso-ruc-4013")).toBeNull();
  });

  it("mientras espera la respuesta dice que consulta a SUNAT, y no antes de tener el RUC completo", async () => {
    let responder: (r: Response) => void = () => {};
    const fetch = vi.fn<(url: string) => Promise<Response>>(() => new Promise((resolver) => { responder = resolver; }));
    vi.stubGlobal("fetch", fetch);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);

    digitar("RUC", "2010006660");
    expect(screen.queryByText("Consultando SUNAT…")).toBeNull();
    fireEvent.change(ruc(), { target: { value: "20100066603" } });
    expect(await screen.findByText("Consultando SUNAT…")).toBeInTheDocument();

    responder(new Response(JSON.stringify(exito(TIENDAS).cuerpo)));
    await waitFor(() => expect(screen.queryByText("Consultando SUNAT…")).toBeNull());
    await waitFor(() => expect(razon().value).toBe("TIENDAS DEL SUR S.A.C."));
  });

  /** Cada respuesta que llega cambia los resultados guardados y vuelve a correr el efecto: el RUC que sigue en vuelo no se puede pedir otra vez. */
  it("un pedido en vuelo no se repite cuando llega la respuesta de otro RUC", async () => {
    const pendientes: Record<string, (r: Response) => void> = {};
    const fetch = vi.fn<(url: string) => Promise<Response>>((url) => new Promise((resolver) => { pendientes[String(url).split("/").pop() ?? ""] = resolver; }));
    vi.stubGlobal("fetch", fetch);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);

    digitar("RUC", "20100066603");
    fireEvent.change(ruc(), { target: { value: "" } });
    digitar("RUC", "20601234565");
    await waitFor(() => expect(Object.keys(pendientes)).toHaveLength(2));

    await act(async () => pendientes["20100066603"](new Response(JSON.stringify(exito(TIENDAS).cuerpo))));

    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it("lo rellenado se puede corregir a mano", async () => {
    stubPadron({ "20100066603": exito(TIENDAS) });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    digitar("RUC", "20100066603");
    await waitFor(() => expect(razon().value).toBe("TIENDAS DEL SUR S.A.C."));

    fireEvent.change(razon(), { target: { value: "TIENDAS DEL SUR SAC" } });
    fireEvent.change(direccion(), { target: { value: "Jr. Otro 456" } });

    expect(razon().value).toBe("TIENDAS DEL SUR SAC");
    expect(direccion().value).toBe("Jr. Otro 456");
  });

  it("no pisa lo que el usuario ya escribió, y ofrece el nombre de SUNAT aparte", async () => {
    stubPadron({ "20100066603": exito(TIENDAS) });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    fireEvent.change(razon(), { target: { value: "Mi cliente de siempre" } });
    fireEvent.change(direccion(), { target: { value: "Av. Mía 1" } });

    digitar("RUC", "20100066603");

    await screen.findByText("Usar este nombre");
    // El nombre se rellena (o no) en el efecto que sigue a la respuesta: se deja correr antes de comprobar que no se tocó.
    await act(async () => {});
    expect(razon().value).toBe("Mi cliente de siempre");
    expect(direccion().value).toBe("Av. Mía 1");
    fireEvent.click(screen.getByRole("button", { name: "Usar este nombre" }));
    expect(razon().value).toBe("TIENDAS DEL SUR S.A.C.");
    expect(screen.queryByText("Usar este nombre")).toBeNull();
  });

  it("al cambiar el RUC quita lo rellenado (no puede quedar el nombre de un cliente bajo el RUC de otro) pero no lo escrito", async () => {
    stubPadron({ "20100066603": exito(TIENDAS) });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    digitar("RUC", "20100066603");
    await waitFor(() => expect(razon().value).toBe("TIENDAS DEL SUR S.A.C."));
    fireEvent.change(direccion(), { target: { value: "Jr. Mía 9" } });

    fireEvent.change(ruc(), { target: { value: "2010006660" } });

    expect(razon().value).toBe("");
    expect(direccion().value).toBe("Jr. Mía 9");
  });

  it("un RUC de baja avisa 4013 y uno no habido avisa 4014, antes de emitir y sin bloquear", async () => {
    stubPadron({ "20160109441": exito(DE_BAJA), "20602160280": exito(NO_HABIDO) });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);

    digitar("RUC", "20160109441");
    expect(await screen.findByTestId("aviso-ruc-4013")).toHaveTextContent("BAJA DE OFICIO");
    expect(screen.getByTestId("aviso-ruc-4014")).toBeInTheDocument();
    // El nombre se rellena en el efecto que sigue a la respuesta, un instante después de que aparezcan los avisos.
    await waitFor(() => expect(razon().value).toBe("QUISPE MAMANI JOSE"));
    expect(direccion().value).toBe("");

    fireEvent.change(ruc(), { target: { value: "" } });
    digitar("RUC", "20602160280");
    expect(await screen.findByTestId("aviso-ruc-4014")).toBeInTheDocument();
    expect(screen.queryByTestId("aviso-ruc-4013")).toBeNull();
    expect(screen.getByRole("button", { name: "Emitir factura" })).not.toBeDisabled();
  });

  it("un RUC que el padrón no tiene avisa y deja digitar los datos a mano", async () => {
    stubPadron({});
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);

    digitar("RUC", "20601234565");

    const aviso = await screen.findByText(/no figura en el padrón de SUNAT/);
    expect(aviso).toHaveTextContent("Puedes digitar los datos a mano");
    fireEvent.change(razon(), { target: { value: "EMPRESA RECIEN INSCRITA SAC" } });
    expect(razon().value).toBe("EMPRESA RECIEN INSCRITA SAC");
    expect(screen.getByRole("button", { name: "Emitir factura" })).not.toBeDisabled();
  });

  it("con el padrón sin cargar dice que la consulta no está disponible", async () => {
    stubPadron({ "20601234565": falla(503, "PADRON_NO_CARGADO") });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);

    digitar("RUC", "20601234565");

    expect(await screen.findByText(/consulta de RUC no está disponible/)).toBeInTheDocument();
  });

  it("agotado el cupo avisa y «Consultar de nuevo» vuelve a pedir", async () => {
    let intento = 0;
    const fetch = stubPadron({ "20100066603": () => (++intento === 1 ? falla(429, "DEMASIADAS_CONSULTAS") : exito(TIENDAS)) });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    digitar("RUC", "20100066603");
    expect(await screen.findByText(/muchas consultas seguidas/)).toBeInTheDocument();
    expect(razon().value).toBe("");

    fireEvent.click(screen.getByRole("button", { name: "Consultar de nuevo" }));

    await waitFor(() => expect(razon().value).toBe("TIENDAS DEL SUR S.A.C."));
    expect(consultas(fetch)).toHaveLength(2);
  });

  it("una red caída avisa, deja digitar a mano y se puede reintentar", async () => {
    let cae = true;
    const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(() =>
      cae ? Promise.reject(new TypeError("Failed to fetch")) : Promise.resolve(new Response(JSON.stringify(exito(TIENDAS).cuerpo))),
    );
    vi.stubGlobal("fetch", fetch);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);

    digitar("RUC", "20100066603");
    expect(await screen.findByText(/No se pudo consultar SUNAT/)).toBeInTheDocument();
    fireEvent.change(razon(), { target: { value: "A mano SAC" } });
    expect(razon().value).toBe("A mano SAC");

    cae = false;
    fireEvent.click(screen.getByRole("button", { name: "Consultar de nuevo" }));
    await screen.findByText(/Encontrado en SUNAT/);
    expect(razon().value).toBe("A mano SAC");
  });

  it("un RUC con el dígito verificador mal no se consulta: no gasta el cupo", () => {
    const fetch = stubPadron({});
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);

    digitar("RUC", "20601234567");

    expect(consultas(fetch)).toEqual([]);
    expect(screen.queryByTestId("consulta-ruc")).toBeNull();
  });

  it("volver a un RUC ya consultado no lo pide otra vez", async () => {
    const fetch = stubPadron({ "20100066603": exito(TIENDAS) });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    digitar("RUC", "20100066603");
    await waitFor(() => expect(razon().value).toBe("TIENDAS DEL SUR S.A.C."));

    fireEvent.change(ruc(), { target: { value: "" } });
    expect(razon().value).toBe("");
    digitar("RUC", "20100066603");

    await waitFor(() => expect(razon().value).toBe("TIENDAS DEL SUR S.A.C."));
    expect(consultas(fetch)).toHaveLength(1);
  });
});

describe("NuevoComprobanteForm — boleta con RUC (#379)", () => {
  it("con DNI no consulta; al elegir RUC sí, y rellena el nombre pero no una dirección que la boleta no muestra", async () => {
    const fetch = stubPadron({ "20100066603": exito(TIENDAS) });
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);

    fireEvent.change(screen.getByLabelText("Número de documento"), { target: { value: "12345678" } });
    expect(consultas(fetch)).toEqual([]);

    fireEvent.change(screen.getByLabelText("Documento del comprador"), { target: { value: "6" } });
    digitar("Número de documento", "20100066603");

    await waitFor(() => expect((screen.getByLabelText("Nombre del comprador") as HTMLInputElement).value).toBe("TIENDAS DEL SUR S.A.C."));
    expect(consultas(fetch)).toEqual(["/api/proxy/consultas/ruc/20100066603"]);
    expect(screen.queryByLabelText(/Dirección/)).toBeNull();

    // Una dirección que el usuario no ve no viaja: la boleta no tiene ese campo.
    fireEvent.change(screen.getByLabelText("Descripción"), { target: { value: "Televisor" } });
    fireEvent.change(screen.getByLabelText(/Precio unit\./), { target: { value: "100" } });
    fireEvent.blur(screen.getByLabelText(/Precio unit\./));
    fireEvent.click(screen.getByRole("button", { name: "Emitir boleta" }));
    await waitFor(() => expect(fetch.mock.calls.some(([url]) => String(url).endsWith("/facturas"))).toBe(true));
    const envio = fetch.mock.calls.find(([url]) => String(url).endsWith("/facturas"));
    expect(JSON.parse(String(envio?.[1]?.body)).cliente).toEqual({ tipo_doc: "6", num_doc: "20100066603", razon_social: "TIENDAS DEL SUR S.A.C." });
  });

  /** Un carné de extranjería o un pasaporte admiten hasta 15 caracteres: uno de 11 dígitos con forma de RUC no es un RUC y no debe gastar el cupo de consultas. */
  it("con carné de extranjería o pasaporte no consulta, aunque el número tenga forma de RUC", async () => {
    const fetch = stubPadron({ "20100066603": exito(TIENDAS) });
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);

    for (const documento of ["4", "7"]) {
      fireEvent.change(screen.getByLabelText("Documento del comprador"), { target: { value: documento } });
      digitar("Número de documento", "20100066603");
    }
    await act(async () => {});

    expect(consultas(fetch)).toEqual([]);
    expect((screen.getByLabelText("Nombre del comprador") as HTMLInputElement).value).toBe("");
  });

  it("al volver a «sin documento» el nombre rellenado se cambia por el habitual", async () => {
    stubPadron({ "20100066603": exito(TIENDAS) });
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);
    fireEvent.change(screen.getByLabelText("Documento del comprador"), { target: { value: "6" } });
    digitar("Número de documento", "20100066603");
    await waitFor(() => expect((screen.getByLabelText("Nombre del comprador") as HTMLInputElement).value).toBe("TIENDAS DEL SUR S.A.C."));

    fireEvent.change(screen.getByLabelText("Documento del comprador"), { target: { value: "-" } });

    expect((screen.getByLabelText("Nombre del comprador") as HTMLInputElement).value).toBe("CLIENTES VARIOS");
  });
});
