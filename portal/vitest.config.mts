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
  },
  resolve: {
    alias: {
      "@": path.resolve(import.meta.dirname, "./src"),
    },
  },
});
