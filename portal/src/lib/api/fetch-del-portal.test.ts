import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * #654: todo lo que el navegador pide al proxy pasa por `fetchDelPortal` (`lib/api/browser.ts`), que es quien abre «Tu sesión terminó» cuando el 401 ya no tiene arreglo. Un `fetch(` suelto
 * hacia `/api/proxy` se saltaría el diálogo y volvería a dejar «No autorizado.» sobre una pantalla muerta. Como una URL puede armarse en otra función (`hrefDeExportacion`, `urlDe`), no se
 * busca el destino: se fija qué archivos pueden llamar a `fetch(` a pelo, cada uno por su motivo. Uno nuevo que lo necesite se suma acá a conciencia; si pide al proxy, usa `fetchDelPortal`.
 */
const RAIZ = join(import.meta.dirname, "..", "..");

const PERMITIDOS: Record<string, string> = {
  "lib/api/browser.ts": "el propio envoltorio",
  "app/api/proxy/[...path]/route.ts": "el proxy reenvía al backend, desde el servidor",
  "lib/api/client.ts": "cliente del backend desde el servidor",
  "lib/api/catalogos.ts": "cliente del backend desde el servidor",
  "lib/api/admin-consumo.ts": "cliente del backend desde el servidor",
  "lib/fetch-fuera-de-la-peticion.ts": "envuelve el fetch global del servidor al arrancar",
  "app/developers/page.tsx": "lee el OpenAPI del backend desde el servidor",
  "app/developers/guia/page.tsx": "el `fetch` está en un ejemplo de código que se muestra, no se ejecuta",
  "components/api-keys/ejemplo-integracion.tsx": "el `fetch` está en un ejemplo de código que se muestra, no se ejecuta",
  "lib/sesion/keep-alive.ts": "/api/auth/sesion: el keep-alive decide por el status y no pasa por el proxy",
  "components/sesion/use-vigilante-de-inactividad.ts": "/api/auth/logout",
  "components/nav/perfil-usuario.tsx": "/api/auth/logout",
  "components/auth/cuenta-suspendida.tsx": "/api/auth/logout",
  "components/legal/aceptar-terminos.tsx": "/api/auth/logout",
  "components/admin/admin-perfil.tsx": "/api/admin/auth/logout: el backoffice tiene su propia sesión",
};

function fuentes(dir: string): string[] {
  return readdirSync(dir).flatMap((entrada) => {
    const ruta = join(dir, entrada);
    if (statSync(ruta).isDirectory()) return fuentes(ruta);
    return /\.tsx?$/.test(entrada) && !/\.test\.tsx?$/.test(entrada) ? [ruta] : [];
  });
}

const relativa = (ruta: string) => ruta.slice(RAIZ.length + 1).replaceAll("\\", "/");

/** Las líneas de código con un `fetch(` (no `fetchDelPortal(` ni `buscar...Fetch(`), sin los comentarios de línea completa. */
function llamadasAFetch(ruta: string): string[] {
  return readFileSync(ruta, "utf8")
    .split("\n")
    .filter((linea) => !/^\s*(\/\/|\/?\*)/.test(linea) && /\bfetch\(/.test(linea));
}

describe("el navegador pide al proxy solo por fetchDelPortal (#654)", () => {
  const conFetch = fuentes(RAIZ)
    .filter((ruta) => !relativa(ruta).startsWith("mocks/"))
    .map((ruta) => ({ nombre: relativa(ruta), lineas: llamadasAFetch(ruta) }))
    .filter(({ lineas }) => lineas.length > 0);

  it("ningún archivo fuera de la lista llama a fetch( a pelo", () => {
    expect(conFetch.map(({ nombre }) => nombre).filter((nombre) => !(nombre in PERMITIDOS))).toEqual([]);
  });

  it("los archivos permitidos siguen existiendo y llamando a fetch( (la lista no se pudre)", () => {
    expect(Object.keys(PERMITIDOS).filter((nombre) => !conFetch.some((archivo) => archivo.nombre === nombre))).toEqual([]);
  });

  it("y ni siquiera ellos piden a /api/proxy con fetch( a pelo, salvo el proxy mismo (que reenvía al backend)", () => {
    const sueltos = conFetch
      .filter(({ nombre }) => nombre !== "app/api/proxy/[...path]/route.ts")
      .flatMap(({ nombre, lineas }) => lineas.filter((linea) => linea.includes("/api/proxy")).map((linea) => `${nombre}: ${linea.trim()}`));
    expect(sueltos).toEqual([]);
  });
});
