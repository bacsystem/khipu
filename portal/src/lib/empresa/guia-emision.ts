import type { EmpresaDetalle } from "@/lib/api/empresas";
import type { Serie } from "@/lib/api/series";
import { enlaceASeccion, seccionesPendientes } from "@/lib/empresa/secciones";

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
export function pasosParaEmitir(empresa: EstadoEmpresa, series: Serie[] | null, hoy: string): PasoGuia[] {
  const pendientes = seccionesPendientes(empresa, hoy);
  const certificadoVencido = empresa.tiene_certificado && pendientes.includes("certificado");
  const solRechazada = Boolean(empresa.credenciales_sol_rechazadas);

  const pasos: PasoGuia[] = [
    {
      id: "certificado",
      titulo: "Certificado digital",
      detalle: !pendientes.includes("certificado")
        ? "Cargado y vigente."
        : certificadoVencido
          ? "El certificado venció: carga uno vigente (.p12 o .pfx)."
          : "Carga el certificado digital (.p12 o .pfx) con el que se firman los comprobantes.",
      listo: !pendientes.includes("certificado"),
      recomendado: false,
      enlace: enlaceASeccion("certificado"),
      accion: certificadoVencido ? "Renovar certificado" : "Cargar certificado",
    },
    {
      id: "sol",
      titulo: "Credenciales SOL",
      detalle: !pendientes.includes("sol")
        ? "Usuario secundario configurado."
        : solRechazada
          ? "SUNAT rechazó las credenciales guardadas: corrígelas para que se reanuden los envíos."
          : "Guarda el usuario SOL secundario y su clave para enviar a SUNAT.",
      listo: !pendientes.includes("sol"),
      recomendado: false,
      enlace: enlaceASeccion("sol"),
      accion: solRechazada ? "Corregir credenciales" : "Guardar credenciales",
    },
    {
      id: "domicilio",
      titulo: "Domicilio fiscal",
      detalle: empresa.domicilio
        ? "Registrado."
        : "Va como dirección del emisor en el XML y en la representación impresa; SUNAT no bloquea la emisión sin ella.",
      listo: Boolean(empresa.domicilio),
      recomendado: true,
      enlace: enlaceASeccion("datos"),
      accion: "Completar domicilio",
    },
  ];

  if (series !== null) {
    const listo = hayCamino(series);
    // Con una serie de factura o boleta ya creada pero inactiva, repetir su número da 409: se aconseja otro.
    const hayInactivas = series.some((s) => !s.activa && (s.tipo === "01" || s.tipo === "03"));
    pasos.push({
      id: "series",
      titulo: "Series de comprobantes",
      detalle: listo
        ? "Hay una serie activa de factura o de boleta."
        : hayInactivas
          ? "Tus series de factura y boleta están inactivas: crea una con otro número (por ejemplo F002 o B002)."
          : "Crea al menos una serie de factura (F001) o de boleta (B001).",
      listo,
      recomendado: false,
      enlace: "/series",
      accion: "Crear serie",
    });
  }

  return pasos;
}

export function hayPasosPendientes(pasos: PasoGuia[]): boolean {
  return pasos.some((p) => !p.listo);
}
