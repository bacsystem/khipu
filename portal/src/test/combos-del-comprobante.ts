import { act, fireEvent, screen, waitFor, within } from "@testing-library/react";
import es from "@/messages/es.json";
import { elegirOpcion, opcionesDe } from "@/test/select-de-prueba";

/**
 * Los combos del formulario de comprobantes (#555) por código, como hacía `fireEvent.change(select, { target: { value } })`. Sus opciones dicen el código al
 * principio («0200 · Exportación de bienes», «10 · Gravado») o entre paréntesis al final («$ Dólares americanos (USD)»); el documento del comprador, por su nombre.
 */
const escapar = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

/** La opción cuyo texto empieza con «código · » o termina en «(código)». */
export const opcionDeCodigo = (codigo: string) => new RegExp(`^${escapar(codigo)} · |\\(${escapar(codigo)}\\)$`);

/** El código de una opción («0200 · …» → «0200», «S/ Soles (PEN)» → «PEN»); `null` si no lo dice («Seleccione»). */
export const codigoDeOpcion = (texto: string) => texto.match(/^(\S+) · /)?.[1] ?? texto.match(/\((\w+)\)$/)?.[1] ?? null;

/** Elige por código en el combo de esa etiqueta. */
export const elegirCodigo = (etiqueta: string | RegExp, codigo: string) => elegirOpcion(screen.getByLabelText(etiqueta), opcionDeCodigo(codigo));

/** Los códigos que ofrece el combo de esa etiqueta, en orden (sin las opciones que no tienen código). */
export const codigosDe = async (etiqueta: string | RegExp) => (await opcionesDe(screen.getByLabelText(etiqueta))).map(codigoDeOpcion).filter((c): c is string => c !== null);

const NOMBRE_DOCUMENTO = es.documentos.identidadBoleta as Record<string, string>;

/** Elige el documento del comprador por su código de catálogo 06 («6» → «RUC», «-» → «Sin documento…»). */
export const elegirDocumento = (codigo: string) => elegirOpcion(screen.getByLabelText("Documento del comprador"), NOMBRE_DOCUMENTO[codigo]);

/** Los códigos de los documentos del comprador ofrecidos, en orden. */
export const documentosOfrecidos = async () => {
  const codigoDe = Object.fromEntries(Object.entries(NOMBRE_DOCUMENTO).map(([codigo, nombre]) => [nombre, codigo]));
  return (await opcionesDe(screen.getByLabelText("Documento del comprador"))).map((texto) => codigoDe[texto]);
};

/**
 * Un `Combobox` del design system (país, unidad de medida): escribe `texto` en su campo para filtrar y hace clic en la opción `opcion`. Espera a que la lista se cierre.
 */
export async function elegirEnCombobox(etiqueta: string | RegExp, texto: string, opcion: string | RegExp = texto) {
  const campo = screen.getByLabelText(etiqueta);
  await act(async () => {
    fireEvent.change(campo, { target: { value: texto } });
  });
  // En jsdom escribir filtra pero no abre la lista (base-ui la abre con el teclado real): se abre con su botón, ya filtrada.
  if (!screen.queryByRole("listbox")) {
    await act(async () => {
      fireEvent.click(within(campo.parentElement as HTMLElement).getByRole("button", { name: "Abrir opciones", hidden: true }));
    });
  }
  const item = await screen.findByRole("option", { name: opcion });
  await act(async () => {
    fireEvent.pointerDown(item, { pointerType: "mouse" });
    fireEvent.click(item);
  });
  await waitFor(() => {
    if (screen.queryByRole("listbox")) throw new Error("la lista del combobox sigue abierta");
  });
}

/** Los textos que ofrece un `Combobox` sin filtrar (abre la lista con su botón y la cierra con Escape). */
export async function opcionesDeCombobox(etiqueta: string | RegExp, etiquetaAbrir = "Abrir opciones"): Promise<string[]> {
  const campo = screen.getByLabelText(etiqueta);
  const boton = within(campo.parentElement as HTMLElement).getByRole("button", { name: etiquetaAbrir });
  await act(async () => {
    fireEvent.click(boton);
  });
  const lista = await screen.findByRole("listbox");
  const textos = within(lista).getAllByRole("option").map((o) => o.textContent?.trim() ?? "");
  await act(async () => {
    fireEvent.keyDown(campo, { key: "Escape" });
  });
  await waitFor(() => {
    if (screen.queryByRole("listbox")) throw new Error("la lista del combobox sigue abierta");
  });
  return textos;
}

/** El nombre de un documento del comprador, lo que muestra su combo. */
export const nombreDeDocumento = (codigo: string) => NOMBRE_DOCUMENTO[codigo];
