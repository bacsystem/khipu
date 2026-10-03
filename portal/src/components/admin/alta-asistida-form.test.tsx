import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AltaAsistidaForm } from "./alta-asistida-form";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const CREADA = {
  cuenta_id: "c1",
  tenant_id: "t1",
  ruc: "20100066603",
  api_key: "fk_secreta123",
  serie: { tipo: "01", serie: "F001" },
  invitacion_enviada: true,
};

function sobre(status: number, cuerpo: object) {
  return new Response(JSON.stringify({ mensaje: null, codigo: null, errores: null, datos: null, ...cuerpo }), { status });
}

function stubFetch(respuesta: Response | (() => Promise<Response>)) {
  const fn = vi.fn(typeof respuesta === "function" ? respuesta : async () => respuesta);
  vi.stubGlobal("fetch", fn);
  return fn;
}

function llenar(etiqueta: RegExp | string, valor: string) {
  fireEvent.change(screen.getByLabelText(etiqueta), { target: { value: valor } });
}

function llenarValido(extra: { telefono?: string; razon?: string } = {}) {
  llenar("Nombre de la cuenta", "Comercial Andina");
  llenar("Correo del cliente", "ana@andina.pe");
  if (extra.telefono) llenar(/Celular/, extra.telefono);
  llenar("RUC", "20100066603");
  llenar("Razón social", extra.razon ?? "COMERCIAL ANDINA SAC");
}

const enviar = () => fireEvent.click(screen.getByRole("button", { name: "Dar de alta" }));

/**
 * Alta asistida (#188). Lo que importa: el administrador nunca define una contraseña, no se manda nada inválido al servidor, y la API key
 * inicial —que se ve una sola vez— queda a la vista con su aviso.
 */
describe("AltaAsistidaForm", () => {
  it("no pide ninguna contraseña: la elige el cliente con la invitación", () => {
    render(<AltaAsistidaForm />);

    expect(screen.queryByLabelText(/contraseña/i)).toBeNull();
    expect(screen.getByText(/elige su propia contraseña/)).toBeTruthy();
  });

  it("parte en Beta, con una factura F001", () => {
    render(<AltaAsistidaForm />);

    expect((screen.getByLabelText("Entorno") as HTMLSelectElement).value).toBe("BETA");
    expect((screen.getByLabelText("Tipo de comprobante") as HTMLSelectElement).value).toBe("01");
    expect((screen.getByLabelText("Serie") as HTMLInputElement).value).toBe("F001");
  });

  it("con el formulario vacío marca los obligatorios y no llama al servidor", async () => {
    const fetch = stubFetch(sobre(201, { estado: "exito", datos: CREADA }));
    render(<AltaAsistidaForm />);

    enviar();

    await waitFor(() => expect(screen.getAllByRole("alert").length).toBeGreaterThanOrEqual(4));
    expect(fetch).not.toHaveBeenCalled();
  });

  it("rechaza un RUC con el dígito verificador mal, sin viajar al servidor", async () => {
    const fetch = stubFetch(sobre(201, { estado: "exito", datos: CREADA }));
    render(<AltaAsistidaForm />);
    llenarValido();
    llenar("RUC", "20100066604");

    enviar();

    await waitFor(() => expect(screen.getByText(/El RUC no es válido/)).toBeTruthy());
    expect(fetch).not.toHaveBeenCalled();
  });

  it("rechaza una serie que no corresponde al tipo (una factura con serie de boleta)", async () => {
    const fetch = stubFetch(sobre(201, { estado: "exito", datos: CREADA }));
    render(<AltaAsistidaForm />);
    llenarValido();
    llenar("Serie", "B001");

    enviar();

    await waitFor(() => expect(screen.getByText(/empieza con F y la de una boleta con B/)).toBeTruthy());
    expect(fetch).not.toHaveBeenCalled();
  });

  it("rechaza un celular que no es peruano, pero no lo exige", async () => {
    const fetch = stubFetch(sobre(201, { estado: "exito", datos: CREADA }));
    render(<AltaAsistidaForm />);
    llenarValido({ telefono: "12345" });

    enviar();

    await waitFor(() => expect(screen.getByText(/Celular inválido/)).toBeTruthy());
    expect(fetch).not.toHaveBeenCalled();
  });

  it("manda el alta tal como el backend la espera, sin teléfono si no se escribió y sin contraseña", async () => {
    const fetch = stubFetch(sobre(201, { estado: "exito", datos: CREADA }));
    render(<AltaAsistidaForm />);
    llenarValido();

    enviar();

    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
    const [url, init] = fetch.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/admin/cuentas");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body as string)).toEqual({
      nombre: "Comercial Andina",
      email: "ana@andina.pe",
      empresa: { ruc: "20100066603", razon_social: "COMERCIAL ANDINA SAC", entorno: "BETA" },
      serie: { tipo: "01", serie: "F001" },
    });
  });

  it("normaliza el celular y respeta el entorno, el tipo y la serie elegidos", async () => {
    const fetch = stubFetch(sobre(201, { estado: "exito", datos: { ...CREADA, serie: { tipo: "03", serie: "B002" } } }));
    render(<AltaAsistidaForm />);
    llenarValido({ telefono: "987-654-321" });
    fireEvent.change(screen.getByLabelText("Entorno"), { target: { value: "PRODUCCION" } });
    fireEvent.change(screen.getByLabelText("Tipo de comprobante"), { target: { value: "03" } });
    llenar("Serie", "b002");

    enviar();

    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
    const cuerpo = JSON.parse((fetch.mock.calls[0] as unknown as [string, RequestInit])[1].body as string);
    expect(cuerpo.telefono).toBe("987654321");
    expect(cuerpo.empresa.entorno).toBe("PRODUCCION");
    expect(cuerpo.serie).toEqual({ tipo: "03", serie: "B002" });
  });

  it("tras el alta muestra la API key una sola vez, con su aviso, y la invitación enviada", async () => {
    stubFetch(sobre(201, { estado: "exito", datos: CREADA }));
    render(<AltaAsistidaForm />);
    llenarValido();

    enviar();

    await waitFor(() => expect(screen.getByTestId("api-key-nueva").textContent).toBe("fk_secreta123"));
    expect(screen.getByRole("heading", { name: "Cliente dado de alta" })).toBeTruthy();
    expect(screen.getByText("API key inicial de la empresa")).toBeTruthy();
    expect(screen.getByText(/no volverá a mostrarse/)).toBeTruthy();
    expect(screen.getByText(/Enviamos la invitación a ana@andina.pe/)).toBeTruthy();
    expect(screen.queryByLabelText("RUC")).toBeNull();
  });

  it("si el correo no salió lo dice claro y dice qué hacer: el alta quedó hecha", async () => {
    stubFetch(sobre(201, { estado: "exito", datos: { ...CREADA, invitacion_enviada: false } }));
    render(<AltaAsistidaForm />);
    llenarValido();

    enviar();

    await waitFor(() => expect(screen.getByText(/No pudimos enviar la invitación a ana@andina.pe/)).toBeTruthy());
    expect(screen.getByText(/¿Olvidaste tu contraseña\?/)).toBeTruthy();
    expect(screen.queryByText(/Enviamos la invitación/)).toBeNull();
    expect(screen.getByTestId("api-key-nueva").textContent).toBe("fk_secreta123");
  });

  it("un correo ya registrado muestra el mensaje del backend y deja corregir el formulario", async () => {
    stubFetch(sobre(409, { estado: "error", codigo: "DUPLICADO", mensaje: "Ya existe una cuenta con ese correo" }));
    render(<AltaAsistidaForm />);
    llenarValido();

    enviar();

    await waitFor(() => expect(screen.getByText("Ya existe una cuenta con ese correo")).toBeTruthy());
    expect((screen.getByLabelText("Correo del cliente") as HTMLInputElement).value).toBe("ana@andina.pe");
    expect(screen.queryByTestId("api-key-nueva")).toBeNull();
  });

  it("un RUC ya registrado también muestra lo que dijo el backend, no el texto del correo", async () => {
    stubFetch(sobre(409, { estado: "error", codigo: "DUPLICADO", mensaje: "Ya existe una empresa con RUC 20100066603" }));
    render(<AltaAsistidaForm />);
    llenarValido();

    enviar();

    await waitFor(() => expect(screen.getByText("Ya existe una empresa con RUC 20100066603")).toBeTruthy());
    expect(screen.queryByText("Ya existe una cuenta con ese correo.")).toBeNull();
  });

  it("si la sesión del administrador venció lo dice y no muestra un «no autorizado» a secas", async () => {
    stubFetch(sobre(401, { estado: "error", codigo: "NO_AUTORIZADO", mensaje: "Sesión de administrador requerida" }));
    render(<AltaAsistidaForm />);
    llenarValido();

    enviar();

    await waitFor(() => expect(screen.getByText(/Tu sesión de administrador expiró/)).toBeTruthy());
  });

  it("un corte de red muestra el error genérico de conexión", async () => {
    stubFetch(async () => {
      throw new TypeError("fallo de red");
    });
    render(<AltaAsistidaForm />);
    llenarValido();

    enviar();

    await waitFor(() => expect(screen.getByText(/No se pudo conectar con el servidor/)).toBeTruthy());
  });

  it("mientras envía bloquea el botón: un doble clic no crea dos altas", async () => {
    let resolver: (r: Response) => void = () => {};
    const fetch = stubFetch(() => new Promise<Response>((r) => (resolver = r)));
    render(<AltaAsistidaForm />);
    llenarValido();

    enviar();
    await waitFor(() => expect((screen.getByRole("button", { name: "Dando de alta…" }) as HTMLButtonElement).disabled).toBe(true));
    fireEvent.click(screen.getByRole("button", { name: "Dando de alta…" }));

    expect(fetch).toHaveBeenCalledTimes(1);
    resolver(sobre(201, { estado: "exito", datos: CREADA }));
    await waitFor(() => expect(screen.getByTestId("api-key-nueva")).toBeTruthy());
  });

  it("«Dar de alta a otro cliente» vuelve a un formulario limpio y la API key anterior desaparece", async () => {
    stubFetch(sobre(201, { estado: "exito", datos: CREADA }));
    render(<AltaAsistidaForm />);
    llenarValido();
    enviar();
    await waitFor(() => expect(screen.getByTestId("api-key-nueva")).toBeTruthy());

    fireEvent.click(screen.getByRole("button", { name: "Dar de alta a otro cliente" }));

    expect(screen.queryByTestId("api-key-nueva")).toBeNull();
    expect((screen.getByLabelText("Correo del cliente") as HTMLInputElement).value).toBe("");
    expect((screen.getByLabelText("RUC") as HTMLInputElement).value).toBe("");
  });

  it("desde el resultado se puede volver a la lista de cuentas", async () => {
    stubFetch(sobre(201, { estado: "exito", datos: CREADA }));
    render(<AltaAsistidaForm />);
    llenarValido();
    enviar();
    await waitFor(() => expect(screen.getByTestId("api-key-nueva")).toBeTruthy());

    expect(screen.getByRole("link", { name: "Ver cuentas" }).getAttribute("href")).toBe("/admin/cuentas");
  });
});
