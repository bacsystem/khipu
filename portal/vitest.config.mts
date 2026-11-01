import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "node:path";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    setupFiles: ["./vitest.setup.ts"],
    // eslint-rules/: las pruebas de las reglas propias de ESLint (#25).
    include: ["src/**/*.test.{ts,tsx}", "eslint-rules/**/*.test.mjs"],
    // Los componentes de navegación del design system (barra superior, menú lateral) leen `usePathname` de `next/navigation`: sin procesarlos, Vitest los deja como
    // dependencia externa y usan el módulo real (que devuelve `null` fuera de Next) en vez del que cada prueba simula con `vi.mock`.
    server: { deps: { inline: [/khipu-design-system/] } },
  },
  resolve: {
    alias: {
      "@": path.resolve(import.meta.dirname, "./src"),
    },
  },
});
