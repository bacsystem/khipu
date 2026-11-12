import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * #637: la actividad de la sesión que se cierra no debe cerrar la siguiente. Todo lugar que llama al logout del cliente (`/api/auth/logout`) borra antes las marcas de
 * inactividad con `reiniciarMarcas()`; uno nuevo que lo olvide dejaría a quien vuelve mañana expulsado en la primera evaluación... o, peor, con la marca de hoy.
 */
const RAIZ = join(import.meta.dirname, "..", "..");

function fuentes(dir: string): string[] {
  return readdirSync(dir).flatMap((entrada) => {
    const ruta = join(dir, entrada);
    if (statSync(ruta).isDirectory()) return fuentes(ruta);
    return /\.tsx?$/.test(entrada) && !/\.test\.tsx?$/.test(entrada) ? [ruta] : [];
  });
}

const LLAMA_AL_LOGOUT = /["'`]\/api\/auth\/logout["'`]/;

describe("los cierres de sesión del cliente reinician las marcas de inactividad", () => {
  const conLogout = fuentes(join(RAIZ, "components")).filter((ruta) => LLAMA_AL_LOGOUT.test(readFileSync(ruta, "utf8")));

  it("encuentra los lugares que cierran la sesión (el menú, la cuenta suspendida, los términos y el vigilante)", () => {
    expect(conLogout.map((ruta) => ruta.slice(RAIZ.length + 1).replaceAll("\\", "/")).sort()).toEqual([
      "components/auth/cuenta-suspendida.tsx",
      "components/legal/aceptar-terminos.tsx",
      "components/nav/perfil-usuario.tsx",
      "components/sesion/use-vigilante-de-inactividad.ts",
    ]);
  });

  it.each(conLogout.map((ruta) => [ruta.slice(RAIZ.length + 1).replaceAll("\\", "/"), ruta]))("%s llama a reiniciarMarcas()", (_nombre, ruta) => {
    expect(readFileSync(ruta, "utf8")).toMatch(/reiniciarMarcas\(/);
  });
});
