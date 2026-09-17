import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  // Todos los tests comparten un único dev server (Turbopack) con mocks en memoria: con más workers se
  // pisan los tiempos de compilación bajo demanda y saltan timeouts de 5s en la primera carga de cada ruta.
  workers: process.env.CI ? 2 : 4,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: "list",
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
    // API_BASE_URL fija para que un .env.local (p. ej. acceso por red) no desvíe los mocks al backend real.
    env: { API_MOCKING: "enabled", API_BASE_URL: "http://localhost:8080" },
  },
});
