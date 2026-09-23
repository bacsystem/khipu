import { request, type FullConfig } from "@playwright/test";

/**
 * Deja el mock en memoria en su estado inicial antes de cada corrida.
 *
 * El dev server se reutiliza entre corridas (`reuseExistingServer`) y el mock solo hacía `resetDb()` al cargar el
 * módulo: cada corrida emitía notas sobre las mismas facturas (gastando el tope 3286) y la de baja dejaba `f-obs`
 * anulada. Medido: corrida 1 verde, corrida 2 con 6 rojos, corrida 3 con 10. Una suite cuya segunda corrida es roja
 * enseña a ignorar el rojo.
 *
 * El reset va por el proxy del portal (que exige sesión y reenvía a `${API_BASE_URL}/v1/__test/reset`), así que
 * primero se inicia sesión con el usuario del mock. El handler solo existe bajo `API_MOCKING=enabled`.
 */
export default async function globalSetup(config: FullConfig) {
  const baseURL = config.projects[0]?.use.baseURL ?? "http://localhost:3100";
  const ctx = await request.newContext({ baseURL });
  try {
    const login = await ctx.post("/api/auth/login", { data: { email: "demo@example.com", password: "Passw0rd1" } });
    if (!login.ok()) throw new Error(`No se pudo iniciar sesión en el mock para reiniciarlo: HTTP ${login.status()}`);
    const reset = await ctx.post("/api/proxy/__test/reset");
    if (!reset.ok()) throw new Error(`El reinicio del mock falló: HTTP ${reset.status()} ${await reset.text()}`);
  } finally {
    await ctx.dispose();
  }
}
