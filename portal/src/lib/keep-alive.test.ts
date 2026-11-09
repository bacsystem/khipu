// @vitest-environment node
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

// #610: con el default de Node (5 s), el proxy de delante (Railway reutiliza sus conexiones unos 60 s) manda peticiones por
// conexiones que el portal acaba de cerrar, y el cliente ve cortes sueltos. El server.js de Next standalone lee KEEP_ALIVE_TIMEOUT.
const leer = (ruta: string) => readFileSync(fileURLToPath(new URL(ruta, import.meta.url)), "utf8");
const PROXY_MS = 60_000;

describe("KEEP_ALIVE_TIMEOUT del portal", () => {
  it("la imagen trae un valor mayor que el del proxy de delante", () => {
    const valor = /^ENV KEEP_ALIVE_TIMEOUT=(\d+)$/m.exec(leer("../../Dockerfile"))?.[1];
    expect(valor, "falta ENV KEEP_ALIVE_TIMEOUT en portal/Dockerfile").toBeDefined();
    expect(Number(valor)).toBeGreaterThan(PROXY_MS);
  });

  it("docker-compose usa el mismo default que la imagen", () => {
    const imagen = /^ENV KEEP_ALIVE_TIMEOUT=(\d+)$/m.exec(leer("../../Dockerfile"))?.[1];
    const compose = /^\s+KEEP_ALIVE_TIMEOUT: \$\{KEEP_ALIVE_TIMEOUT:-(\d+)\}$/m.exec(leer("../../../docker-compose.yml"))?.[1];
    expect(compose, "falta KEEP_ALIVE_TIMEOUT en el servicio portal de docker-compose.yml").toBeDefined();
    expect(compose).toBe(imagen);
  });
});
