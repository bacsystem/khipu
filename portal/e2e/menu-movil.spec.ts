import { expect, test } from "@playwright/test";

/**
 * #505: en el teléfono el menú lateral es un cajón. El del design system no se desplaza, y en una pantalla baja (un iPhone con la barra del navegador, unos 640 px) el menú de
 * usuario —donde están el tema, «Apariencia…» y «Cerrar sesión»— quedaba fuera de la pantalla y no había cómo llegar a él.
 */
test.use({ viewport: { width: 390, height: 640 }, isMobile: true, hasTouch: true });

test("en una pantalla baja se llega al menú de usuario desde el cajón y se abre «Apariencia…»", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("panel@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/inicio$/);

  await page.getByRole("button", { name: "Abrir menú" }).click();
  const cajon = page.getByRole("dialog");
  const menuDeUsuario = cajon.getByRole("button", { name: "Menú de usuario" });
  // La «píldora» del entorno de la barra superior del design system se oculta en pantallas angostas: en el teléfono el entorno se ve en el chip del cajón.
  await expect(cajon).toContainText("Beta");

  // Lo último del cajón se alcanza desplazándolo, como con el dedo. No vale `scrollIntoViewIfNeeded`: desplaza por script, y eso funciona hasta con `overflow: hidden`.
  await cajon.hover();
  await page.mouse.wheel(0, 3000);
  await expect(menuDeUsuario).toBeInViewport({ ratio: 1 });

  await menuDeUsuario.click();
  await page.getByRole("menuitem", { name: "Apariencia…" }).click();
  await expect(page.getByRole("dialog").filter({ has: page.getByRole("heading", { name: "Apariencia", level: 2 }) })).toBeVisible();
});
