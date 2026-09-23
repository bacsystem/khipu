import { describe, expect, it } from "vitest";
import { contactoUrl, registroAbierto } from "./acceso";

describe("registroAbierto", () => {
  it("la variable manda cuando está definida", () => {
    expect(registroAbierto({ REGISTRO_ABIERTO: "true", NODE_ENV: "production" } as NodeJS.ProcessEnv)).toBe(true);
    expect(registroAbierto({ REGISTRO_ABIERTO: "false", NODE_ENV: "development" } as NodeJS.ProcessEnv)).toBe(false);
    // Cualquier otro valor cuenta como cerrado: un "1" o un "yes" mal copiado no debe abrir el autoservicio.
    expect(registroAbierto({ REGISTRO_ABIERTO: "1", NODE_ENV: "development" } as NodeJS.ProcessEnv)).toBe(false);
  });

  it("sin la variable: cerrado en producción, abierto en desarrollo y test", () => {
    expect(registroAbierto({ NODE_ENV: "production" } as NodeJS.ProcessEnv)).toBe(false);
    expect(registroAbierto({ NODE_ENV: "development" } as NodeJS.ProcessEnv)).toBe(true);
    expect(registroAbierto({ NODE_ENV: "test" } as NodeJS.ProcessEnv)).toBe(true);
  });
});

describe("contactoUrl", () => {
  it("devuelve null cuando no hay URL o está en blanco", () => {
    expect(contactoUrl({ NODE_ENV: "test" } as NodeJS.ProcessEnv)).toBeNull();
    expect(contactoUrl({ CONTACTO_URL: "   ", NODE_ENV: "test" } as NodeJS.ProcessEnv)).toBeNull();
    expect(contactoUrl({ CONTACTO_URL: "mailto:hola@ejemplo.pe", NODE_ENV: "test" } as NodeJS.ProcessEnv)).toBe("mailto:hola@ejemplo.pe");
  });
});
