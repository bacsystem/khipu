import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  // Reinicia el mock en memoria al empezar cada corrida: con `reuseExistingServer` el estado sobrevivía entre
  // corridas y la segunda ya fallaba (topes gastados, facturas anuladas). Playwright arranca el webServer antes.
  globalSetup: "./e2e/global-setup.ts",
  fullyParallel: true,
  // Todos los tests comparten un único dev server (Turbopack) con mocks en memoria: con más workers se
  // pisan los tiempos de compilación bajo demanda y saltan timeouts de 5s en la primera carga de cada ruta.
  workers: process.env.CI ? 2 : 4,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: "list",
  // La primera carga de cada ruta compila bajo demanda (el detalle de comprobante ya supera los 5 s por defecto
  // con 4 workers en paralelo): el margen cubre esa compilación, no un fallo funcional.
  expect: { timeout: 15_000 },
  use: {
    baseURL: "http://localhost:3100",
    trace: "on-first-retry",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "npm run dev -- -p 3100",
    url: "http://localhost:3100",
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
    // API_BASE_URL fija, y a un puerto MUERTO: MSW intercepta lo que tiene handler y deja pasar el resto, así que
    // con :8001 (donde suele escuchar el backend real) una petición sin mock llegaba al backend del desarrollador.
    // Con :8999 falla en la conexión y el test lo denuncia. También evita que un .env.local desvíe los mocks.
    env: { API_MOCKING: "enabled", API_BASE_URL: "http://localhost:8999" },
  },
});
