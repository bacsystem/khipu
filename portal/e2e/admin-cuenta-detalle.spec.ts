import { expect, test } from "@playwright/test";
import { entrarComoAdmin } from "./admin-sesion";

// El mock (src/mocks/data.ts) siembra «Panadería Sol» con un detalle completo: dos usuarios (uno sin verificar), una empresa con
// certificado por vencer en 10 días, un comprobante y una acción de la bitácora.
const ID_SOL = "00000000-0000-4000-8000-000000000001";
const ID_NUEVA = "00000000-0000-4000-8000-000000000012";

test("sin sesión, el detalle redirige al login del backoffice", async ({ page }) => {
  await page.goto(`/admin/cuentas/${ID_SOL}`);
  await expect(page).toHaveURL(/\/admin\/login/);
});

test("desde el listado, el nombre de la cuenta lleva a su detalle", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto("/admin/cuentas?q=sol");

  await page.getByRole("link", { name: "Panadería Sol" }).click();

  await expect(page).toHaveURL(new RegExp(`/admin/cuentas/${ID_SOL}$`));
  await expect(page.getByRole("heading", { name: "Panadería Sol", level: 1 })).toBeVisible();
});

test("el detalle muestra usuarios, empresas con su certificado, comprobantes y bitácora", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto(`/admin/cuentas/${ID_SOL}`);
  const detalle = page.getByTestId("cuenta-detalle");

  const usuarios = detalle.getByRole("region", { name: "Usuarios" });
  await expect(usuarios.locator("tbody tr")).toHaveCount(2);
  await expect(usuarios.getByText("Verificado")).toHaveCount(1);
  await expect(usuarios.getByText("Sin verificar")).toHaveCount(1);
  // Los roles son los del dominio (`Rol`): el mock no inventa otros.
  await expect(usuarios.locator("tbody tr", { hasText: "ana@sol.pe" }).getByRole("cell", { name: "ADMIN", exact: true })).toBeVisible();
  await expect(usuarios.locator("tbody tr", { hasText: "beto@sol.pe" }).getByRole("cell", { name: "EMISOR", exact: true })).toBeVisible();

  const empresas = detalle.getByRole("region", { name: "Empresas" });
  await expect(empresas.getByText("PANADERIA SOL SAC")).toBeVisible();
  await expect(empresas.getByText(/Vence el .* \(10 días\)/)).toBeVisible();
  await expect(empresas.getByText("Cargadas")).toBeVisible();

  const comprobantes = detalle.getByRole("region", { name: "Comprobantes recientes" });
  await expect(comprobantes.getByText("F001-00000007")).toBeVisible();

  const eventos = detalle.getByRole("region", { name: "Acciones del administrador" });
  await expect(eventos.locator("tbody tr")).toHaveCount(3);
  await expect(eventos.getByText("Alta de la cuenta")).toBeVisible();
  // Quién actuó: la clave de plataforma no se presenta como un administrador.
  await expect(eventos.locator("tbody tr", { hasText: "Alta de una empresa" }).getByText("Clave de plataforma")).toBeVisible();
  await expect(eventos.locator("tbody tr", { hasText: "Alta de la cuenta" }).getByText("Administrador")).toBeVisible();
  // Una acción que el portal todavía no conoce se muestra con su código, no se esconde.
  await expect(eventos.getByText("SUSPENDER_CUENTA")).toBeVisible();
});

/** Desde #182 hay una acción, y solo esa: suspender. Las demás (impersonar, planes…) llegan en sus issues. */
test("la única acción sobre la cuenta es suspenderla: ningún otro botón", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto(`/admin/cuentas/${ID_SOL}`);

  const detalle = page.getByTestId("cuenta-detalle");
  await expect(detalle).toBeVisible();
  await expect(detalle.getByRole("button")).toHaveCount(1);
  await expect(detalle.getByTestId("suspender-cuenta")).toBeVisible();
});

test("una cuenta sin empresas ni movimientos muestra los estados vacíos", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto(`/admin/cuentas/${ID_NUEVA}`);

  await expect(page.getByText("Esta cuenta todavía no tiene empresas.")).toBeVisible();
  await expect(page.getByText("Todavía no emitió comprobantes.")).toBeVisible();
  await expect(page.getByText("Ningún administrador actuó sobre esta cuenta.")).toBeVisible();
});

test("un id que no existe o que no es un UUID da 404, sin llegar al detalle", async ({ page }) => {
  await entrarComoAdmin(page);

  const inexistente = await page.goto("/admin/cuentas/00000000-0000-4000-8000-0000000000ff");
  expect(inexistente?.status()).toBe(404);

  const basura = await page.goto("/admin/cuentas/no-es-un-uuid");
  expect(basura?.status()).toBe(404);
});
