import { readFileSync, readdirSync, statSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

/**
 * khipu es un producto peruano: el portal habla en **tuteo** («revisa», «intenta», «sube»). El voseo rioplatense
 * («revisá», «intentá», «subís») se colaba de a poco en los textos nuevos y dejaba el portal con dos registros
 * mezclados, que es lo que hace que un producto no parezca local.
 *
 * **Se enumeran verbos, no formas.** La primera versión de este test listaba formas de voseo y se le escaparon
 * «Subís», «Configurás», «Emitís» y «Recibís»: las formas son un conjunto abierto y enumerarlas nunca alcanza. La
 * segunda versión marcaba por terminación, y entonces confundía futuros legítimos —«emitirá», «tendrás»— con voseo,
 * porque separarlos necesita saber de qué verbo viene cada palabra. Esta versión enumera los **verbos** que usa el
 * producto y deriva de cada uno sus dos formas de voseo, que es una regla mecánica y sin falsos positivos:
 *
 *     -ar → revisá / revisás      -er → devolvé / devolvés      -ir → subí / subís
 *
 * Agregar un verbo es una palabra. Y cuando alguien escribe «Subís», el verbo «subir» está en esta lista casi seguro,
 * que es lo que hace que esta versión sí atrape lo que las otras dos dejaron pasar.
 *
 * El `/developers` usa **usted** a propósito: es la documentación para integradores, otra audiencia y otro registro.
 * Eso no es voseo y este test no lo toca.
 */
const VERBOS = [
  // Los del producto: lo que el portal le pide o le cuenta al usuario.
  "emitir", "anular", "enviar", "recibir", "descargar", "subir", "cargar", "configurar", "crear", "guardar",
  "revisar", "verificar", "validar", "firmar", "consultar", "buscar", "filtrar", "ordenar", "seleccionar",
  "elegir", "confirmar", "cancelar", "continuar", "completar", "corregir", "editar", "borrar", "eliminar",
  "reintentar", "reenviar", "actualizar", "recargar", "activar", "desactivar", "registrar", "ingresar",
  "acreditar", "devolver", "numerar", "reasignar", "administrar", "integrar", "probar", "generar", "copiar",
  // Los corrientes, que aparecen en cualquier texto.
  "tener", "poder", "querer", "saber", "hacer", "poner", "venir", "salir", "decir", "leer", "escribir",
  "usar", "llamar", "pasar", "quedar", "esperar", "mirar", "dejar", "tomar", "entrar", "volver", "seguir",
  "empezar", "terminar", "necesitar", "pedir", "abrir", "cerrar", "sumar", "restar", "agregar", "quitar",
  "bajar", "avisar", "contar", "considerar", "aplicar", "marcar", "vivir", "recordar", "olvidar", "cambiar",
];

/** Irregulares y formas con pronombre pegado, que la derivación mecánica no produce. */
const EXTRAS = [
  "sos", "vos", "andá", "andate", "fijate", "quedate", "acordate", "decime", "decinos", "contanos", "avisanos",
  "escribinos", "mandanos", "mandá", "ponela", "ponelo", "cargalo", "cargala", "dale", "tenés", "vení", "vení",
];

/** De un infinitivo, sus dos formas de voseo: imperativo y presente de indicativo. */
export function formasDeVoseo(infinitivo: string): [string, string] {
  const raiz = infinitivo.slice(0, -2);
  const term = infinitivo.slice(-2);
  if (term === "ar") return [`${raiz}á`, `${raiz}ás`];
  if (term === "er") return [`${raiz}é`, `${raiz}és`];
  return [`${raiz}í`, `${raiz}ís`];
}

const FORMAS = [...new Set([...VERBOS.flatMap(formasDeVoseo), ...EXTRAS])];
const PATRON = new RegExp(`(?<!\\p{L})(${FORMAS.join("|")})(?!\\p{L})`, "giu");

/** Las palabras en voseo que aparecen en un texto. */
export function voseoEn(texto: string): string[] {
  return [...texto.matchAll(PATRON)].map((m) => m[1]);
}

function archivosDeTexto(dir: string): string[] {
  const salida: string[] = [];
  for (const entrada of readdirSync(dir)) {
    const completo = path.join(dir, entrada);
    if (statSync(completo).isDirectory()) salida.push(...archivosDeTexto(completo));
    else if (/\.(tsx?|json)$/.test(entrada)) salida.push(completo);
  }
  return salida;
}

describe("registro lingüístico del portal", () => {
  it("no usa voseo en ningún archivo de src", () => {
    const raiz = path.join(__dirname, "..");
    const hallazgos: string[] = [];

    for (const archivo of archivosDeTexto(raiz)) {
      if (archivo.endsWith("registro-linguistico.test.ts")) continue; // enumera verbos a propósito
      readFileSync(archivo, "utf8")
        .split("\n")
        .forEach((linea, i) => {
          for (const palabra of voseoEn(linea)) {
            hallazgos.push(`${path.relative(raiz, archivo)}:${i + 1}  «${palabra}»  ${linea.trim().slice(0, 80)}`);
          }
        });
    }

    expect(hallazgos, `Voseo encontrado. El portal habla en tuteo peruano:\n${hallazgos.join("\n")}`).toEqual([]);
  });

  it("deriva bien las dos formas de cada conjugación", () => {
    expect(formasDeVoseo("revisar")).toEqual(["revisá", "revisás"]);
    expect(formasDeVoseo("devolver")).toEqual(["devolvé", "devolvés"]);
    expect(formasDeVoseo("subir")).toEqual(["subí", "subís"]);
  });

  it("detecta el voseo que las versiones anteriores dejaban pasar", () => {
    // Las cuatro que se escaparon de la lista de formas, y por las que ahora se enumeran verbos.
    expect(voseoEn("Subís tu certificado")).toEqual(["Subís"]);
    expect(voseoEn("Configurás tus series")).toEqual(["Configurás"]);
    expect(voseoEn("Emitís desde el portal")).toEqual(["Emitís"]);
    expect(voseoEn("Recibís la constancia")).toEqual(["Recibís"]);
    expect(voseoEn("Revisá tu conexión e intentá de nuevo")).toEqual(["Revisá"]);
    expect(voseoEn("la llamás desde tu sistema, o usás el portal")).toEqual(["llamás", "usás"]);
  });

  it("no marca el tuteo, el usted, ni los futuros que se le parecen", () => {
    expect(voseoEn("Revisa tu conexión e intenta de nuevo")).toEqual([]);
    expect(voseoEn("Revise su conexión e intente de nuevo")).toEqual([]);
    expect(voseoEn("Subes tu certificado y configuras tus series")).toEqual([]);
    // El futuro es la trampa de detectar por terminación: «emitirá» y «tendrás» son correctos.
    expect(voseoEn("El comprobante se emitirá y tendrás la constancia en segundos")).toEqual([]);
    expect(voseoEn("Se enviará, no volverá a mostrarse y SUNAT responderá")).toEqual([]);
    expect(voseoEn("Está así, aquí y también después: según el interés del país")).toEqual([]);
  });
});
