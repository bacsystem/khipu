import { expect, test } from "@playwright/test";
import { entrarComoAdmin } from "./admin-sesion";

// playwright.config pone SUPPORT_URL=https://ayuda.khipu.test y SUPPORT_EMAIL=soporte@khipu.test (#250). Sin configurar no se ve nada: lo cubre Vitest.
const ID_SOL = "00000000-0000-4000-8000-000000000001";

test("el login dice a dónde escribir si no puedes entrar", async ({ page }) => {
  await page.goto("/login");
  await expect(page.getByText("¿Necesitas ayuda?")).toBeVisible();
  await expect(page.getByRole("link", { name: "Centro de ayuda" })).toHaveAttribute("href", "https://ayuda.khipu.test");
  await expect(page.getByRole("link", { name: "soporte@khipu.test" })).toHaveAttribute("href", "mailto:soporte@khipu.test");
});

test("el panel del cliente lo muestra en el pie y en su plan", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);
  await expect(page.getByText("¿Necesitas ayuda?")).toBeVisible();

  await page.goto("/cuenta/plan");
  // Una vez bajo «comunícate con el equipo de khipu» y otra en el pie.
  await expect(page.getByText("¿Necesitas ayuda?")).toHaveCount(2);
});

test("la ficha de una cuenta del backoffice ofrece escribir a soporte con la cuenta en el asunto", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto(`/admin/cuentas/${ID_SOL}`);

  const enlace = page.getByRole("link", { name: "Escribir a soporte" });
  await expect(enlace).toHaveAttribute("href", new RegExp(`^mailto:soporte@khipu\\.test\\?subject=Cuenta%20.+%20\\(${ID_SOL}\\)$`));
});
