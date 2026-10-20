import { CODIGOS, ESTADOS, HTTP } from "@/components/developers/errores-datos";
import { ENTORNOS, INDICE_ENTORNOS, LIMITES, REINTENTOS } from "@/components/developers/entornos-datos";
import { CASOS } from "@/components/developers/guia/casos-emision";
import { INDICE_ERRORES, INDICE_GUIA } from "@/components/developers/indices";
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

/** Los temas de la guía que se buscan por su nombre propio aunque no sean un título de sección. Cada uno lleva a una ancla que existe (lo comprueba una prueba). */
const TEMAS_DE_LA_GUIA: Array<[id: string, titulo: string, href: string, detalle: string]> = [
  ["verificar-firma", "Verificar la firma de un webhook", "/developers/guia#webhooks", "X-Khipu-Signature HMAC SHA-256 firma entrega secreto Node Python PHP"],
  ["idempotency", "Idempotency-Key: reintento seguro", "/developers/guia#idempotency-key", "Idempotency-Key reintentar timeout duplicado 24 horas"],
  ["correo", "Enviar el comprobante al cliente por correo", "/developers/guia#buenas-practicas", "correo PDF XML CDR adquirente /correo"],
  ["emitir-sin-enviar", "Emitir ahora y enviar después", "/developers/guia#sin-enviar", "enviar_automatico false FIRMADO POST enviar lote"],
];

/** Todo lo que se busca, en el orden en que se ofrece. Sale de los mismos datos que muestran las páginas, así que una fila nueva en una tabla se encuentra sola. */
export const ENTRADAS: readonly EntradaDeBusqueda[] = [
  ...INDICE_GUIA.filter((s) => !s.label.startsWith("· ")).map((s) => entrada(`guia-${s.id}`, s.label, "Guía de emisión", `/developers/guia#${s.id}`)),
  ...CASOS.map((c) => entrada(`caso-${c.id}`, c.titulo, "Guía · caso de emisión", `/developers/guia#${c.id}`, c.cuando)),
  ...TEMAS_DE_LA_GUIA.map(([id, titulo, href, detalle]) => entrada(`tema-${id}`, titulo, "Guía de emisión", href, detalle)),
  ...INDICE_ENTORNOS.map((s) => entrada(`entorno-${s.id}`, s.label, "Entornos y límites", `/developers/entornos#${s.id}`)),
  ...ENTORNOS.map(([aspecto, beta, produccion], i) => entrada(`entorno-fila-${i}`, aspecto, "Entornos · BETA y producción", "/developers/entornos#entornos", `${beta} ${produccion}`)),
  ...LIMITES.map(([que, limite, cuando], i) => entrada(`limite-${i}`, que, "Entornos · límites y cuotas", "/developers/entornos#limites", `${limite} ${cuando}`)),
  ...REINTENTOS.map(([que, cuanto], i) => entrada(`reintento-${i}`, que, "Entornos · reintentos y vigencias", "/developers/entornos#reintentos", cuanto)),
  ...INDICE_ERRORES.map((s) => entrada(`errores-${s.id}`, s.label, "Errores y estados", `/developers/errores#${s.id}`)),
  ...ESTADOS.map(([estado, significado]) => entrada(`estado-${estado}`, estado, "Estado del comprobante", "/developers/errores#estados", significado)),
  ...HTTP.map(([http, cuando]) => entrada(`http-${http}`, http, "Código HTTP", "/developers/errores#http", cuando)),
  ...CODIGOS.map(([codigo, http, significado]) => entrada(`codigo-${codigo}`, codigo, `Código de error · HTTP ${http}`, "/developers/errores#codigos", significado)),
];
