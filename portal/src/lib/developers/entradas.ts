import { codigosDeError, codigosHttp, estados } from "@/components/developers/errores-datos";
import { entornos, indiceEntornos, limites, reintentos } from "@/components/developers/entornos-datos";
import { casos } from "@/components/developers/guia/casos-emision";
import { indiceErrores, indiceGuia } from "@/components/developers/indices";
import { interpolar, type Mensajes } from "@/i18n/mensajes";
import type { EntradaDeBusqueda } from "./indice";

/** Lo que viaja al navegador de cada entrada: el detalle se corta, porque el buscador solo necesita las primeras palabras para ubicar y los casos traen párrafos enteros. */
const MAXIMO_DEL_DETALLE = 320;

const entrada = (id: string, titulo: string, seccion: string, href: string, detalle = ""): EntradaDeBusqueda => ({
  id,
  titulo,
  seccion,
  href,
  detalle: detalle.length > MAXIMO_DEL_DETALLE ? detalle.slice(0, MAXIMO_DEL_DETALLE) : detalle,
});

/**
 * Los temas de la guía que se buscan por su nombre propio aunque no sean un título de sección: el id, la clave de su título y sus palabras en
 * `messages.developers.buscador.temas`, y el ancla. Cada uno lleva a una ancla que existe (lo comprueba una prueba).
 */
const TEMAS_DE_LA_GUIA: Array<[id: string, clave: keyof Mensajes["developers"]["buscador"]["temas"], href: string]> = [
  ["verificar-firma", "verificarFirma", "/developers/guia#webhooks"],
  ["idempotency", "idempotency", "/developers/guia#idempotency-key"],
  ["correo", "correo", "/developers/guia#buenas-practicas"],
  ["emitir-sin-enviar", "emitirSinEnviar", "/developers/guia#sin-enviar"],
  ["conciliar-rvie", "conciliarRvie", "/developers/guia#sire"],
];

/**
 * Todo lo que se busca, en el orden en que se ofrece y en el idioma de `messages`. Sale de los mismos datos que muestran las páginas, así que una fila nueva en una tabla se
 * encuentra sola.
 */
export function entradas(messages: Mensajes): EntradaDeBusqueda[] {
  const s = messages.developers.buscador.secciones;
  const temas = messages.developers.buscador.temas;
  return [
    ...indiceGuia(messages)
      .filter((i) => !i.esCaso)
      .map((i) => entrada(`guia-${i.id}`, i.label, s.guia, `/developers/guia#${i.id}`)),
    ...casos(messages).map((c) => entrada(`caso-${c.id}`, c.titulo, s.caso, `/developers/guia#${c.id}`, c.cuando)),
    ...TEMAS_DE_LA_GUIA.map(([id, clave, href]) => entrada(`tema-${id}`, temas[clave][0], s.guia, href, temas[clave][1])),
    ...indiceEntornos(messages).map((i) => entrada(`entorno-${i.id}`, i.label, s.entornos, `/developers/entornos#${i.id}`)),
    ...entornos(messages).map(([aspecto, beta, produccion], i) =>
      entrada(`entorno-fila-${i}`, aspecto, s.entornosFila, "/developers/entornos#entornos", `${beta} ${produccion}`),
    ),
    ...limites(messages).map(([que, limite, cuando], i) => entrada(`limite-${i}`, que, s.limite, "/developers/entornos#limites", `${limite} ${cuando}`)),
    ...reintentos(messages).map(([que, cuanto], i) => entrada(`reintento-${i}`, que, s.reintento, "/developers/entornos#reintentos", cuanto)),
    ...indiceErrores(messages).map((i) => entrada(`errores-${i.id}`, i.label, s.errores, `/developers/errores#${i.id}`)),
    ...estados(messages).map(([estado, significado]) => entrada(`estado-${estado}`, estado, s.estado, "/developers/errores#estados", significado)),
    ...codigosHttp(messages).map(([http, cuando]) => entrada(`http-${http}`, http, s.http, "/developers/errores#http", cuando)),
    ...codigosDeError(messages).map(([codigo, http, significado]) =>
      entrada(`codigo-${codigo}`, codigo, interpolar(s.codigo, { http }), "/developers/errores#codigos", significado),
    ),
  ];
}
