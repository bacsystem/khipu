import { expect, type Locator, type Page } from "@playwright/test";

/**
 * #557: en el modal de emisión los campos del comprador (RUC, razón social, dirección; en boleta documento, número y nombre) van plegados bajo el campo «Cliente»
 * («Comprador» en boleta). Esto los despliega como el usuario: «Ingresar a mano» sin cliente (en boleta pasa de «Clientes varios» al DNI) o «Editar datos» en la
 * tarjeta del cliente. Si ya están a la vista, no hace nada.
 */
export async function abrirDatosDelCliente(dialogo: Locator) {
  // Desplegados sin cliente, «Ingresar a mano» pasa a decir «Descartar datos» (con `aria-expanded="true"`).
  const boton = dialogo
    .getByRole("button", { name: "Ingresar a mano" })
    .or(dialogo.getByRole("button", { name: "Editar datos" }))
    .or(dialogo.getByRole("button", { name: "Descartar datos" }));
  await expect(boton).toBeVisible();
  if ((await boton.getAttribute("aria-expanded")) !== "true") await boton.click();
  await expect(boton).toHaveAttribute("aria-expanded", "true");
}

/**
 * #560: el modal ofrece percepción, retención, tipo de operación, más opciones, anticipos y referencias solo con «Mostrar opciones avanzadas al emitir» encendida
 * (apagada por defecto en el mock). Se enciende para la empresa de la sesión por el mismo proxy que usa el portal; hay que llamarlo antes de abrir el modal, que lee la
 * preferencia al montarse. Ningún spec la necesita apagada, así que dejarla encendida no estorba a los que corren en paralelo; `global-setup` la apaga en cada corrida.
 */
export async function conOpcionesAvanzadas(page: Page) {
  const res = await page.request.put("/api/proxy/empresa/preferencias-emision", { data: { opciones_avanzadas: true } });
  expect(res.ok()).toBe(true);
}
