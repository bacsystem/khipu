import type { Mensajes } from "@/i18n/mensajes";
import type { EmpresaDetalle } from "@/lib/api/empresas";
import type { Serie } from "@/lib/api/series";
import { enlaceASeccion, seccionesPendientes } from "@/lib/empresa/secciones";
import es from "@/messages/es.json";

export type IdPaso = "certificado" | "sol" | "domicilio" | "series";

export type PasoGuia = {
  id: IdPaso;
  titulo: string;
  /** Lo que hay que hacer (pendiente) o lo que ya está (listo). */
  detalle: string;
  listo: boolean;
  /** El backend no bloquea la emisión sin este paso (el domicilio fiscal); se pide igual porque va en el XML y en el PDF. */
  recomendado: boolean;
  enlace: string;
  /** Texto de la acción del enlace mientras está pendiente. */
  accion: string;
};

type EstadoEmpresa = Pick<
  EmpresaDetalle,
  "tiene_certificado" | "certificado_vigencia_hasta" | "tiene_credenciales_sol" | "credenciales_sol_rechazadas" | "domicilio"
>;

/** Una serie de factura o de boleta activa: con eso ya se puede emitir; las notas cuelgan de una de las dos. */
function hayCamino(series: Serie[]): boolean {
  return series.some((s) => s.activa && (s.tipo === "01" || s.tipo === "03"));
}

/**
 * Los pasos para empezar a emitir, en el orden en que conviene hacerlos (#375). Certificado y SOL salen de `seccionesPendientes`, que
 * a su vez usa `faltaParaEmitir` (las reglas de `Tenant.exigirListoParaEmitir`): aquí no hay una tercera copia. `series` en `null` es
 * «no se pudo leer»: ese paso se omite en vez de afirmar que falta. `hoy` en `YYYY-MM-DD` (Lima).
 */
export function pasosParaEmitir(empresa: EstadoEmpresa, series: Serie[] | null, hoy: string, messages: Mensajes = es): PasoGuia[] {
  const t = messages.guiaEmision;
  const pendientes = seccionesPendientes(empresa, hoy);
  const certificadoVencido = empresa.tiene_certificado && pendientes.includes("certificado");
  const solRechazada = Boolean(empresa.credenciales_sol_rechazadas);

  const pasos: PasoGuia[] = [
    {
      id: "certificado",
      titulo: t.certificado.titulo,
      detalle: !pendientes.includes("certificado") ? t.certificado.listo : certificadoVencido ? t.certificado.vencido : t.certificado.falta,
      listo: !pendientes.includes("certificado"),
      recomendado: false,
      enlace: enlaceASeccion("certificado"),
      accion: certificadoVencido ? t.certificado.renovar : t.certificado.cargar,
    },
    {
      id: "sol",
      titulo: t.sol.titulo,
      detalle: !pendientes.includes("sol") ? t.sol.listo : solRechazada ? t.sol.rechazada : t.sol.falta,
      listo: !pendientes.includes("sol"),
      recomendado: false,
      enlace: enlaceASeccion("sol"),
      accion: solRechazada ? t.sol.corregir : t.sol.guardar,
    },
    {
      id: "domicilio",
      titulo: t.domicilio.titulo,
      detalle: empresa.domicilio ? t.domicilio.listo : t.domicilio.falta,
      listo: Boolean(empresa.domicilio),
      recomendado: true,
      enlace: enlaceASeccion("datos"),
      accion: t.domicilio.accion,
    },
  ];

  if (series !== null) {
    const listo = hayCamino(series);
    // Con una serie de factura o boleta ya creada pero inactiva, repetir su número da 409: se aconseja otro.
    const hayInactivas = series.some((s) => !s.activa && (s.tipo === "01" || s.tipo === "03"));
    pasos.push({
      id: "series",
      titulo: t.series.titulo,
      detalle: listo ? t.series.listo : hayInactivas ? t.series.inactivas : t.series.falta,
      listo,
      recomendado: false,
      enlace: "/series",
      accion: t.series.accion,
    });
  }

  return pasos;
}

export function hayPasosPendientes(pasos: PasoGuia[]): boolean {
  return pasos.some((p) => !p.listo);
}
