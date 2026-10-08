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

/** Llena los tres pasos con datos válidos. Los pasos están montados todos (solo se ocultan), así que se llenan sin navegar. */
function llenarValido(extra: { telefono?: string; razon?: string } = {}) {
  llenar("Correo del cliente", "ana@andina.pe");
  llenar("Nombre del cliente", "Comercial Andina");
  llenar("Celular", extra.telefono ?? "987654321");
  llenar("RUC", "20100066603");
  llenar("Razón social", extra.razon ?? "COMERCIAL ANDINA SAC");
}

/** El número del paso en curso, según el indicador (`aria-current="step"`). */
const pasoActual = () => document.querySelector('[aria-current="step"]')?.textContent;

const siguiente = () => fireEvent.click(screen.getByRole("button", { name: /Siguiente/ }));

/** Avanza un paso y espera a que el indicador cambie (la validación del paso es asíncrona). */
async function avanzar() {
  const antes = pasoActual();
  siguiente();
  await waitFor(() => expect(pasoActual()).not.toBe(antes));
}

/** Desde el paso 1, pasa los dos primeros y aprieta «Dar de alta» en el tercero. */
async function enviar() {
  await avanzar();
  await avanzar();
  fireEvent.click(screen.getByRole("button", { name: "Dar de alta" }));
}

/** Reenvío desde el último paso (tras un error del servidor el asistente se queda ahí). */
const reenviar = () => fireEvent.click(screen.getByRole("button", { name: "Dar de alta" }));

/**
 * Alta asistida (#188), en tres pasos dentro de un modal. Lo que importa: el administrador nunca define una contraseña, no se manda nada
 * inválido al servidor (cada paso valida sus campos antes de dejar seguir), y la API key inicial —que se ve una sola vez— queda a la vista
 * con su aviso.
 */
describe("AltaAsistidaForm", () => {
  it("no pide ninguna contraseña: la elige el cliente con la invitación", () => {
    render(<AltaAsistidaForm />);

    expect(screen.queryByLabelText(/contraseña/i)).toBeNull();
    expect(screen.getByText(/llega la invitación para crear su contraseña/)).toBeTruthy();
  });

  it("parte en el paso 1, en Beta, con una factura F001", () => {
    render(<AltaAsistidaForm />);

    expect(pasoActual()).toBe("1");
    expect((screen.getByLabelText("Entorno") as HTMLSelectElement).value).toBe("BETA");
    expect((screen.getByLabelText("Tipo de comprobante") as HTMLSelectElement).value).toBe("01");
    expect((screen.getByLabelText("Serie") as HTMLInputElement).value).toBe("F001");
  });

  it("un paso con los obligatorios vacíos no deja seguir, los marca y no llama al servidor", async () => {
    const fetch = stubFetch(sobre(201, { estado: "exito", datos: CREADA }));
    render(<AltaAsistidaForm />);

    siguiente();

    await waitFor(() => expect(screen.getByText("Ingresa el nombre del cliente")).toBeTruthy());
    expect(screen.getByText("Ingresa el correo del cliente")).toBeTruthy();
    expect(screen.getByText("Ingresa el celular del cliente")).toBeTruthy();
    expect(pasoActual()).toBe("1");
    expect(fetch).not.toHaveBeenCalled();
  });

  it("el paso 1 solo valida sus campos: no marca la empresa antes de llegar a ella", async () => {
    render(<AltaAsistidaForm />);
    llenar("Correo del cliente", "ana@andina.pe");
    llenar("Nombre del cliente", "Comercial Andina");
    llenar("Celular", "987654321");

    await avanzar();

    expect(pasoActual()).toBe("2");
    expect(screen.queryByText(/El RUC/)).toBeNull();
  });

  it("«Atrás» vuelve al paso anterior sin perder lo escrito", async () => {
    render(<AltaAsistidaForm />);
    llenarValido();
    await avanzar();

    fireEvent.click(screen.getByRole("button", { name: /Atrás/ }));

    expect(pasoActual()).toBe("1");
    expect((screen.getByLabelText("Correo del cliente") as HTMLInputElement).value).toBe("ana@andina.pe");
  });

  it("«Cancelar» en el primer paso cierra sin enviar", () => {
    const fetch = stubFetch(sobre(201, { estado: "exito", datos: CREADA }));
    const alCancelar = vi.fn();
    render(<AltaAsistidaForm alCancelar={alCancelar} />);

    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));

    expect(alCancelar).toHaveBeenCalledTimes(1);
    expect(fetch).not.toHaveBeenCalled();
  });

  it("rechaza un RUC con el dígito verificador mal en el paso 2, sin viajar al servidor", async () => {
    const fetch = stubFetch(sobre(201, { estado: "exito", datos: CREADA }));
    render(<AltaAsistidaForm />);
    llenarValido();
    llenar("RUC", "20100066604");
    await avanzar();

    siguiente();

    await waitFor(() => expect(screen.getByText(/El RUC no es válido/)).toBeTruthy());
    expect(pasoActual()).toBe("2");
    expect(fetch).not.toHaveBeenCalled();
  });

  it("rechaza una serie que no corresponde al tipo (una factura con serie de boleta)", async () => {
    const fetch = stubFetch(sobre(201, { estado: "exito", datos: CREADA }));
    render(<AltaAsistidaForm />);
    llenarValido();
    llenar("Serie", "B001");

    await enviar();

    await waitFor(() => expect(screen.getByText(/empieza con F y la de una boleta con B/)).toBeTruthy());
    expect(fetch).not.toHaveBeenCalled();
  });

  it("rechaza un celular que no es peruano en el paso 1", async () => {
    const fetch = stubFetch(sobre(201, { estado: "exito", datos: CREADA }));
    render(<AltaAsistidaForm />);
    llenarValido({ telefono: "12345" });

    siguiente();

    await waitFor(() => expect(screen.getByText(/Celular inválido/)).toBeTruthy());
    expect(pasoActual()).toBe("1");
    expect(fetch).not.toHaveBeenCalled();
  });

  it("Enter en un paso intermedio avanza, no envía", async () => {
    const fetch = stubFetch(sobre(201, { estado: "exito", datos: CREADA }));
    render(<AltaAsistidaForm />);
    llenarValido();

    fireEvent.submit(screen.getByLabelText("Nombre del cliente").closest("form")!);

    await waitFor(() => expect(pasoActual()).toBe("2"));
    expect(fetch).not.toHaveBeenCalled();
  });

  it("manda el alta tal como el backend la espera, con el celular y sin contraseña", async () => {
    const fetch = stubFetch(sobre(201, { estado: "exito", datos: CREADA }));
    render(<AltaAsistidaForm />);
    llenarValido();

    await enviar();

    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
    const [url, init] = fetch.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/admin/cuentas");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body as string)).toEqual({
      nombre: "Comercial Andina",
      email: "ana@andina.pe",
      telefono: "987654321",
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

    await enviar();

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

    await enviar();

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

    await enviar();

    await waitFor(() => expect(screen.getByText(/No pudimos enviar la invitación a ana@andina.pe/)).toBeTruthy());
    expect(screen.getByText(/¿Olvidaste tu contraseña\?/)).toBeTruthy();
    expect(screen.queryByText(/Enviamos la invitación/)).toBeNull();
    expect(screen.getByTestId("api-key-nueva").textContent).toBe("fk_secreta123");
  });

  it("un correo ya registrado muestra el mensaje del backend y deja corregir el formulario", async () => {
    stubFetch(sobre(409, { estado: "error", codigo: "DUPLICADO", mensaje: "Ya existe una cuenta con ese correo" }));
    render(<AltaAsistidaForm />);
    llenarValido();

    await enviar();

    await waitFor(() => expect(screen.getByText("Ya existe una cuenta con ese correo")).toBeTruthy());
    expect((screen.getByLabelText("Correo del cliente") as HTMLInputElement).value).toBe("ana@andina.pe");
    expect(screen.queryByTestId("api-key-nueva")).toBeNull();
  });

  it("un RUC ya registrado también muestra lo que dijo el backend, no el texto del correo", async () => {
    stubFetch(sobre(409, { estado: "error", codigo: "DUPLICADO", mensaje: "Ya existe una empresa con RUC 20100066603" }));
    render(<AltaAsistidaForm />);
    llenarValido();

    await enviar();

    await waitFor(() => expect(screen.getByText("Ya existe una empresa con RUC 20100066603")).toBeTruthy());
    expect(screen.queryByText("Ya existe una cuenta con ese correo.")).toBeNull();
  });

  /** H3: el aviso salía en el paso 3, lejos del campo; había que adivinar dónde estaba el error y volver con «Atrás». */
  it("un correo ya registrado lleva al paso 1 y marca el correo", async () => {
    stubFetch(sobre(409, { estado: "error", codigo: "DUPLICADO", mensaje: "Ya existe una cuenta con ese correo" }));
    render(<AltaAsistidaForm />);
    llenarValido();

    await enviar();

    await waitFor(() => expect(pasoActual()).toContain("1"));
    const correo = screen.getByLabelText("Correo del cliente");
    expect(correo.getAttribute("aria-invalid")).toBe("true");
    expect(correo.closest("fieldset")!.hidden).toBe(false);
  });

  it("un RUC ya registrado lleva al paso 2 y marca el RUC", async () => {
    stubFetch(sobre(409, { estado: "error", codigo: "DUPLICADO", mensaje: "Ya existe una empresa con RUC 20100066603" }));
    render(<AltaAsistidaForm />);
    llenarValido();

    await enviar();

    await waitFor(() => expect(pasoActual()).toContain("2"));
    expect(screen.getByLabelText("RUC").getAttribute("aria-invalid")).toBe("true");
  });

  /** H2: al pasar a Boleta la serie se quedaba en «F001» y el formulario la rechazaba. */
  it("al elegir boleta la serie sugerida pasa sola a B001, y al volver a factura vuelve a F001", async () => {
    render(<AltaAsistidaForm />);
    const serie = () => (screen.getByLabelText("Serie") as HTMLInputElement).value;

    fireEvent.change(screen.getByLabelText("Tipo de comprobante"), { target: { value: "03" } });
    await waitFor(() => expect(serie()).toBe("B001"));

    fireEvent.change(screen.getByLabelText("Tipo de comprobante"), { target: { value: "01" } });
    await waitFor(() => expect(serie()).toBe("F001"));
  });

  it("una serie que el administrador escribió no se pisa al cambiar el tipo", async () => {
    render(<AltaAsistidaForm />);
    llenar("Serie", "B002");

    fireEvent.change(screen.getByLabelText("Tipo de comprobante"), { target: { value: "03" } });

    await waitFor(() => expect((screen.getByLabelText("Tipo de comprobante") as HTMLSelectElement).value).toBe("03"));
    expect((screen.getByLabelText("Serie") as HTMLInputElement).value).toBe("B002");
  });

  it("si la sesión del administrador venció lo dice y no muestra un «no autorizado» a secas", async () => {
    stubFetch(sobre(401, { estado: "error", codigo: "NO_AUTORIZADO", mensaje: "Sesión de administrador requerida" }));
    render(<AltaAsistidaForm />);
    llenarValido();

    await enviar();

    await waitFor(() => expect(screen.getByText(/Tu sesión de administrador expiró/)).toBeTruthy());
  });

  /** #219: tras un corte no se sabe si el alta se hizo; con la clave de idempotencia reenviar es seguro, y el mensaje lo dice. */
  it("un corte de red invita a reenviar sin cambiar nada", async () => {
    stubFetch(async () => {
      throw new TypeError("fallo de red");
    });
    render(<AltaAsistidaForm />);
    llenarValido();

    await enviar();

    await waitFor(() => expect(screen.getByText(/Vuelve a enviar sin cambiar nada/)).toBeTruthy());
  });

  const claveDe = (fetch: ReturnType<typeof stubFetch>, n: number) =>
    new Headers((fetch.mock.calls[n] as unknown as [string, RequestInit])[1].headers).get("Idempotency-Key");

  it("reenviar el mismo formulario lleva la misma clave; cambiarlo, otra", async () => {
    const fetch = stubFetch(async () => {
      throw new TypeError("fallo de red");
    });
    render(<AltaAsistidaForm />);
    llenarValido();

    await enviar();
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByRole("button", { name: "Dar de alta" })).toBeTruthy());
    reenviar();
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.getByRole("button", { name: "Dar de alta" })).toBeTruthy());
    llenar("Razón social", "OTRA RAZON SOCIAL SAC");
    reenviar();
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(3));

    expect(claveDe(fetch, 0)).toMatch(/^[0-9a-f-]{36}$/);
    expect(claveDe(fetch, 1)).toBe(claveDe(fetch, 0));
    expect(claveDe(fetch, 2)).not.toBe(claveDe(fetch, 0));
  });

  it("dar de alta a otro cliente con los mismos datos estrena clave", async () => {
    const fetch = stubFetch(sobre(201, { estado: "exito", datos: CREADA }));
    render(<AltaAsistidaForm />);
    llenarValido();
    await enviar();
    await waitFor(() => expect(screen.getByTestId("api-key-nueva")).toBeTruthy());
    fireEvent.click(screen.getByRole("button", { name: "Dar de alta a otro cliente" }));
    llenarValido();
    await enviar();
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));

    expect(claveDe(fetch, 1)).not.toBe(claveDe(fetch, 0));
  });

  it("mientras envía bloquea el botón y avisa al modal: un doble clic no crea dos altas", async () => {
    let resolver: (r: Response) => void = () => {};
    const fetch = stubFetch(() => new Promise<Response>((r) => (resolver = r)));
    const alCambiarEnvio = vi.fn();
    render(<AltaAsistidaForm alCambiarEnvio={alCambiarEnvio} />);
    llenarValido();

    await enviar();
    await waitFor(() => expect((screen.getByRole("button", { name: "Dando de alta…" }) as HTMLButtonElement).disabled).toBe(true));
    fireEvent.click(screen.getByRole("button", { name: "Dando de alta…" }));

    expect(fetch).toHaveBeenCalledTimes(1);
    expect(alCambiarEnvio).toHaveBeenLastCalledWith(true);
    resolver(sobre(201, { estado: "exito", datos: CREADA }));
    await waitFor(() => expect(screen.getByTestId("api-key-nueva")).toBeTruthy());
    // El aviso al modal sale de un efecto, que puede correr un render después de que aparezca la API key: se espera, no se supone.
    await waitFor(() => expect(alCambiarEnvio).toHaveBeenLastCalledWith(false));
  });

  it("«Dar de alta a otro cliente» vuelve al paso 1 con un formulario limpio y la API key anterior desaparece", async () => {
    stubFetch(sobre(201, { estado: "exito", datos: CREADA }));
    render(<AltaAsistidaForm />);
    llenarValido();
    await enviar();
    await waitFor(() => expect(screen.getByTestId("api-key-nueva")).toBeTruthy());

    fireEvent.click(screen.getByRole("button", { name: "Dar de alta a otro cliente" }));

    expect(screen.queryByTestId("api-key-nueva")).toBeNull();
    expect(pasoActual()).toBe("1");
    expect((screen.getByLabelText("Correo del cliente") as HTMLInputElement).value).toBe("");
    expect((screen.getByLabelText("RUC") as HTMLInputElement).value).toBe("");
  });

  it("desde el resultado, «Listo» cierra el modal", async () => {
    stubFetch(sobre(201, { estado: "exito", datos: CREADA }));
    const alTerminar = vi.fn();
    render(<AltaAsistidaForm alTerminar={alTerminar} />);
    llenarValido();
    await enviar();
    await waitFor(() => expect(screen.getByTestId("api-key-nueva")).toBeTruthy());

    fireEvent.click(screen.getByRole("button", { name: "Listo" }));

    expect(alTerminar).toHaveBeenCalledTimes(1);
  });
});
