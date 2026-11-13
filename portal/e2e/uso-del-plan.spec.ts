import { expect, test } from "@playwright/test";

/**
 * #498: «Plan y consumo» muestra también lo que la cuenta ocupa de las API keys, los usuarios y los RUC de su plan, no solo los documentos: un límite que se enseña es un
 * límite que se cumple. La cuenta del mock (plan Gratis: 1 RUC, 1 usuario, 1 API key) tiene su empresa, su usuario y su key, así que ya está en el tope de los tres.
 *
 * Cuenta propia y no la demo: `equipo.spec.ts` suma usuarios e invitaciones a la demo y `api-keys.spec.ts` crea keys, y como el mock en memoria vive toda la corrida, el uso
 * de la demo dependía de qué specs habían corrido antes (11 / 1 usuarios tras el equipo) y el «justo en el tope» se rompía.
 */
test("la página del plan muestra lo que se usa de API keys, usuarios y RUC contra el tope", async ({ page }) => {
  await page.goto("/login?next=/cuenta/plan");
  await page.getByLabel("Correo electrónico").fill("uso-del-plan@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/^[^?]*\/cuenta\/plan$/);
  await expect(page.getByRole("heading", { name: "Lo que usas de tu plan" })).toBeVisible();

  for (const [clave, etiqueta] of [["apiKeys", "API keys activas"], ["usuarios", "Usuarios"], ["rucs", "Empresas (RUC)"]] as const) {
    const fila = page.getByTestId(`uso-${clave}`);
    // La barra de progreso del design system repite la etiqueta en texto solo para lectores de pantalla (#567): la que se ve es la primera.
    await expect(fila.getByText(etiqueta, { exact: true }).first()).toBeVisible();
    await expect(fila.getByRole("progressbar")).toBeVisible();
    await expect(fila).toContainText("1 / 1");
  }
  await expect(page.getByText(/Los años de retención son una referencia de tu plan/)).toBeVisible();
  // Estar justo en el tope (1 / 1) es lo normal en el plan Gratis: se dice bajo su barra, sin un banner de alerta por recurso.
  await expect(page.getByTestId("uso-usuarios")).toContainText("Llegaste al tope de usuarios de tu plan");
  await expect(page.getByRole("alert").filter({ hasText: "Llegaste al tope" })).toHaveCount(0);
});
