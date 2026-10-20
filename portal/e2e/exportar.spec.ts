import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);
});

/** #321: «Exportar» baja los comprobantes de la lista con sus filtros, en CSV o en Excel, con el nombre que propone la API. */
test("exporta a CSV los comprobantes de la lista con sus filtros", async ({ page }) => {
  test.slow();
  await page.goto("/comprobantes?tipo=01");
  await page.getByRole("button", { name: "Exportar" }).click();
  const panel = page.getByTestId("exportar-comprobantes");
  await panel.getByLabel(/CSV/).check();

  const peticion = page.waitForRequest((r) => r.url().includes("/api/proxy/facturas/exportacion"));
  const descarga = page.waitForEvent("download");
  await panel.getByTestId("exportar-descargar").click();

  expect(new URL((await peticion).url()).searchParams.get("tipo")).toBe("01");
  const archivo = await descarga;
  expect(archivo.suggestedFilename()).toMatch(/^comprobantes-\d{4}-\d{2}-\d{2}\.csv$/);
  const contenido = await readFile((await archivo.path())!, "utf8");
  expect(contenido.startsWith("﻿tipo,serie,numero,fecha_emision,")).toBe(true);
  const filas = contenido.split("\r\n").filter(Boolean).slice(1);
  expect(filas.length).toBeGreaterThan(0);
  expect(filas.every((f) => f.startsWith("01,"))).toBe(true);
  await expect(panel).toHaveCount(0);
});

test("exporta a Excel con el rango de fechas en el nombre", async ({ page }) => {
  test.slow();
  await page.goto("/comprobantes?desde=2026-01-01");
  await page.getByRole("button", { name: "Exportar" }).click();
  const descarga = page.waitForEvent("download");
  await page.getByTestId("exportar-descargar").click();
  expect((await descarga).suggestedFilename()).toMatch(/^comprobantes-2026-01-01_\d{4}-\d{2}-\d{2}\.xlsx$/);
});
