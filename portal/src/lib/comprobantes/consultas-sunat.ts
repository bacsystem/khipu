import { esDeAgente, esDeBoleta, type Comprobante } from "@/lib/api/facturas";
import type { DatosDeValidez } from "@/components/comprobantes/validez-button";

/** Estados desde los que se puede pedir a SUNAT la constancia que falta (#315): `RecuperarCdrService` acepta `ENVIADO` y `ERROR_ENVIO`. */
const ESTADOS_SIN_CONSTANCIA = new Set(["ENVIADO", "ERROR_ENVIO"]);

/** ¿Se ofrece «Recuperar constancia»? Solo facturas y sus notas: SUNAT no expone `getStatusCdr` para boletas ni notas de boleta (`Comprobante.esDeBoleta`). */
export function puedeRecuperarCdr(c: Pick<Comprobante, "tipo" | "estado_documento" | "nota">): boolean {
  // #40: getStatusCdr tampoco cubre la retención ni la percepción (el backend responde CDR_NO_CONSULTABLE).
  return ESTADOS_SIN_CONSTANCIA.has(c.estado_documento) && !esDeBoleta(c) && !esDeAgente(c);
}

/** El RUC del emisor, primer tramo del nombre de archivo (`<RUC>-<tipo>-<serie>-<número>`); `null` si no se puede leer. */
export function rucDelEmisor(nombreArchivo: string | null | undefined): string | null {
  const ruc = nombreArchivo?.split("-")[0];
  return ruc && /^\d{11}$/.test(ruc) ? ruc : null;
}

/** Estados de un comprobante que todavía no salió hacia SUNAT: no hay nada que consultar. */
const SIN_SALIR = new Set(["RECIBIDO", "FIRMADO", "INVALIDO", "DESCARTADO"]);

/**
 * Los datos de `GET /v1/consultas/validez` de un comprobante, o `null` si no se ofrece (todavía no salió, o no se sabe el RUC del emisor). Un comprador sin documento («-») no
 * manda sus criterios: SUNAT los compararía con un tipo de documento que no existe.
 */
export function datosDeValidez(c: Pick<Comprobante, "tipo" | "serie" | "numero" | "fecha_emision" | "estado_documento" | "nombre_archivo" | "receptor" | "totales">): DatosDeValidez | null {
  const ruc = rucDelEmisor(c.nombre_archivo);
  if (!ruc || SIN_SALIR.has(c.estado_documento)) return null;
  const identificado = c.receptor !== null && c.receptor.tipo_doc !== "-";
  return {
    ruc,
    tipo: c.tipo,
    serie: c.serie,
    numero: c.numero,
    tipoDocReceptor: identificado ? c.receptor!.tipo_doc : undefined,
    numDocReceptor: identificado ? c.receptor!.num_doc : undefined,
    fecha: c.fecha_emision,
    monto: c.totales.total,
  };
}
