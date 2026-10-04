import type { ReactNode } from "react";
import { Badge } from "@/components/ui/badge";
import type { EstadoCertificado } from "@/lib/api/admin-cuenta-detalle";
import type { EstadoCuentaAdmin } from "@/lib/api/admin-suspension";
import { formatearFecha } from "@/lib/formato";
import { messages } from "@/lib/messages";
import { cn } from "@/lib/utils";

export type Tono = "ok" | "aviso" | "error" | "neutro";

const TONOS: Record<Tono, string> = {
  ok: "bg-success text-success-foreground border-success-border",
  aviso: "bg-warning text-warning-foreground border-warning-border",
  error: "bg-destructive/10 text-destructive border-destructive-border",
  neutro: "bg-secondary text-muted-foreground border-border",
};

/** Una etiqueta de estado del backoffice: el tono dice de un vistazo si la cosa está bien, pide atención o está mal. */
export function Etiqueta({ tono, children }: { tono: Tono; children: ReactNode }) {
  return <Badge className={cn("border px-2.5 py-1 text-[11px] font-medium whitespace-nowrap", TONOS[tono])}>{children}</Badge>;
}

const c = messages.admin.detalle.empresas;

/** El texto y el tono del certificado de una empresa: el administrador ve de un vistazo cuáles piden atención. */
export function certificadoEnPalabras(estado: EstadoCertificado): { texto: string; tono: Tono } {
  switch (estado.tipo) {
    case "ninguno":
      return { texto: c.sinCertificado, tono: "neutro" };
    case "sin_fecha":
      return { texto: c.certificadoSinFecha, tono: "aviso" };
    case "vencido":
      return { texto: c.certificadoVencido.replace("{fecha}", formatearFecha(estado.hasta)), tono: "error" };
    case "por_vencer":
      return {
        texto: (estado.dias === 0 ? c.certificadoVence : c.certificadoPorVencer).replace("{fecha}", formatearFecha(estado.hasta)).replace("{dias}", String(estado.dias)),
        tono: "aviso",
      };
    case "vigente":
      return { texto: c.certificadoVigente.replace("{fecha}", formatearFecha(estado.hasta)), tono: "ok" };
  }
}

/** El estado del certificado como etiqueta. `data-estado` deja a las pruebas (y a los estilos) distinguir el estado sin leer el texto. */
export function CertificadoEtiqueta({ estado }: { estado: EstadoCertificado }) {
  const { texto, tono } = certificadoEnPalabras(estado);
  return (
    <span data-estado={estado.tipo}>
      <Etiqueta tono={tono}>{texto}</Etiqueta>
    </span>
  );
}

const TONO_DE_CUENTA: Record<EstadoCuentaAdmin, Tono> = { ACTIVA: "ok", SUSPENDIDA: "error", BAJA: "neutro" };

/**
 * El estado de una cuenta: «Suspendida» (#182) en rojo, para que el administrador la vea de un vistazo, y «De baja» (#201) en neutro: el cliente que
 * se fue no es una alarma. `data-estado-cuenta` es para las pruebas y los estilos.
 */
export function EstadoCuentaEtiqueta({ estado }: { estado: EstadoCuentaAdmin }) {
  return (
    <span data-estado-cuenta={estado}>
      <Etiqueta tono={TONO_DE_CUENTA[estado]}>{messages.admin.estadoCuenta[estado]}</Etiqueta>
    </span>
  );
}
