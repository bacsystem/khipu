import { fireEvent, screen } from "@testing-library/react";
import { vi } from "vitest";

/**
 * Lo que las pruebas de los formularios con RUC (#575) necesitan del padrón de SUNAT: contribuyentes de ejemplo, un `fetch` que contesta la consulta de cada
 * RUC y cuenta los pedidos, y un dígito a la vez al escribir (que es como se dispara la consulta en la vida real).
 */
export const TIENDAS = {
  ruc: "20100066603", razon_social: "TIENDAS DEL SUR S.A.C.", estado: "ACTIVO", condicion: "HABIDO", activo: true, habido: true, ubigeo: "150101",
  direccion: "AV. REPUBLICA DE PANAMA NRO. 2457", actualizado_al: "2026-10-16T09:30:00Z",
};
export const DE_BAJA = {
  ruc: "20160109441", razon_social: "QUISPE MAMANI JOSE", estado: "BAJA DE OFICIO", condicion: "NO HABIDO", activo: false, habido: false,
  actualizado_al: "2026-10-16T09:30:00Z",
};
export const COMERCIAL = {
  ruc: "20602160280", razon_social: "COMERCIAL VERDE E.I.R.L.", estado: "ACTIVO", condicion: "HABIDO", activo: true, habido: true,
  direccion: "JR. DE LA UNION NRO. 100", ubigeo: "150101", actualizado_al: "2026-10-16T09:30:00Z",
};

export type RespuestaDePrueba = { status: number; cuerpo: unknown };
export const exito = (datos: unknown): RespuestaDePrueba => ({ status: 200, cuerpo: { estado: "exito", datos, mensaje: null, codigo: null, errores: null } });
export const falla = (status: number, codigo: string): RespuestaDePrueba => ({ status, cuerpo: { estado: "error", datos: null, mensaje: "x", codigo, errores: null } });

/** Contesta la consulta de cada RUC con lo indicado (un RUC sin respuesta no figura: 404); cualquier otra ruta, `otras(url)` o un éxito vacío. */
export function stubPadron(porRuc: Record<string, RespuestaDePrueba>, otras?: (url: string, init?: RequestInit) => RespuestaDePrueba | undefined) {
  const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>((url, init) => {
    const ruc = String(url).match(/\/consultas\/ruc\/(\d+)/)?.[1];
    const { status, cuerpo } = ruc ? (porRuc[ruc] ?? falla(404, "NO_ENCONTRADO")) : (otras?.(String(url), init) ?? exito(null));
    return Promise.resolve(new Response(JSON.stringify(cuerpo), { status }));
  });
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

/** Las URLs de las consultas al padrón que salieron, en orden. */
export const consultasAlPadron = (fetch: ReturnType<typeof stubPadron>) =>
  fetch.mock.calls.filter(([url]) => String(url).includes("/consultas/ruc/")).map(([url]) => String(url));

/** Como un usuario: un dígito a la vez. */
export function digitar(campo: HTMLElement, valor: string) {
  for (let i = 1; i <= valor.length; i++) fireEvent.change(campo, { target: { value: valor.slice(0, i) } });
}

export const estadoDeLaConsulta = () => screen.getByTestId("consulta-ruc").getAttribute("data-estado");
