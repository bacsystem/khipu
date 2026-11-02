import { fireEvent, screen } from "@testing-library/react";

/**
 * #557: los campos del comprador (RUC, razón social, dirección; en boleta documento, número y nombre) van plegados bajo el campo «Cliente». Esto los despliega como
 * lo haría el usuario: «Ingresar a mano» sin cliente (en boleta pasa de «Clientes varios» al DNI) o «Editar datos» en la tarjeta. Si ya están a la vista, no hace nada.
 */
export function abrirDatosDelCliente() {
  const boton = screen.queryByRole("button", { name: "Ingresar a mano" }) ?? screen.queryByRole("button", { name: "Editar datos" });
  if (boton && boton.getAttribute("aria-expanded") !== "true") fireEvent.click(boton);
}

/** El campo «Cliente» (o «Comprador» en boleta). */
export const campoCliente = () => (screen.queryByLabelText("Cliente") ?? screen.getByLabelText("Comprador")) as HTMLInputElement;

/** Escribe en el campo «Cliente»; con `confirmar`, pulsa Enter (un DNI se toma al confirmar, un RUC al completarse). */
export function escribirCliente(texto: string, { confirmar = false }: { confirmar?: boolean } = {}) {
  const campo = campoCliente();
  fireEvent.change(campo, { target: { value: texto } });
  if (confirmar) fireEvent.keyDown(campo, { key: "Enter" });
}
