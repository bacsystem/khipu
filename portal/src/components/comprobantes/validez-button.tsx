"use client";

import { useMessages } from "next-intl";
import { useRef, useState } from "react";
import { Button } from "khipu-design-system/ui/button";
import { interpolar, type Mensajes } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { apiRequest, noSeSabeSiLlego } from "@/lib/api/browser";
import type { Validez } from "@/lib/api/validez";

/**
 * El texto del resultado: el estado en palabras, las observaciones de SUNAT (solo la API REST) y, si se cayó a SOAP, por qué. `estados` es lo que SUNAT contesta a
 * `validaCDPcriterios`, dicho para el emisor; `estadosApi` (#318), lo que contesta la consulta integrada por la API REST (`estadoCp`), con sus propios códigos.
 */
function textoDeValidez(v: Validez, messages: Mensajes): string {
  const t = messages.comprobantes.validez;
  const tabla: Record<string, string> = v.fuente === "API_REST" ? t.estadosApi : t.estados;
  const partes = [tabla[v.estado] ?? interpolar(t.respondio, { codigo: v.codigo ?? "", mensaje: v.mensaje ?? "" }).trim()];
  if (v.observaciones?.length) partes.push(interpolar(t.observaciones, { lista: v.observaciones.join(" ") }));
  if (v.aviso) partes.push(`(${v.aviso})`);
  return partes.join(" ");
}

export type DatosDeValidez = {
  ruc: string;
  tipo: string;
  serie: string;
  numero: number;
  tipoDocReceptor?: string;
  numDocReceptor?: string;
  fecha: string;
  monto: number;
};

/**
 * Consultar en SUNAT si el comprobante es válido (#315): por la API REST si la empresa cargó sus credenciales API SUNAT, si no `billValidService` (#318); lo
 * decide el backend. Solo lee; no cambia el comprobante.
 */
export function ValidezButton({ datos }: { datos: DatosDeValidez }) {
  const messages = useMessages();
  const t = messages.comprobantes.validez;
  const mensajeError = useMensajeError();
  const [consultando, setConsultando] = useState(false);
  const [resultado, setResultado] = useState<{ tono: "ok" | "aviso" | "error"; texto: string } | null>(null);
  const enCursoRef = useRef(false);

  async function consultar() {
    if (enCursoRef.current) return;
    enCursoRef.current = true;
    setConsultando(true);
    setResultado(null);
    const q = new URLSearchParams({ ruc: datos.ruc, tipo: datos.tipo, serie: datos.serie, numero: String(datos.numero), fecha: datos.fecha, monto: datos.monto.toFixed(2) });
    if (datos.tipoDocReceptor && datos.numDocReceptor) {
      q.set("tipo_doc_receptor", datos.tipoDocReceptor);
      q.set("num_doc_receptor", datos.numDocReceptor);
    }
    const res = await apiRequest<Validez>(`/api/proxy/consultas/validez?${q.toString()}`, { method: "GET" });
    enCursoRef.current = false;
    setConsultando(false);
    if (noSeSabeSiLlego(res)) {
      setResultado({ tono: "error", texto: t.cortado });
      return;
    }
    if (res.estado !== "exito" || !res.datos) {
      setResultado({ tono: "error", texto: res.mensaje ?? mensajeError(res.codigo) });
      return;
    }
    const v = res.datos;
    const texto = textoDeValidez(v, messages);
    setResultado({ tono: v.estado === "ACEPTADO" ? "ok" : v.estado === "ERROR_CONSULTA" ? "error" : "aviso", texto });
  }

  return (
    <div className="flex flex-col items-end gap-1.5">
      <Button variant="outline" disabled={consultando} onClick={consultar} data-testid="consultar-validez">
        {consultando ? messages.comprobantes.acciones.consultandoSunat : t.consultar}
      </Button>
      {resultado ? (
        <p
          role={resultado.tono === "error" ? "alert" : "status"}
          className={resultado.tono === "error" ? "max-w-md text-right text-sm text-destructive" : resultado.tono === "ok" ? "max-w-md text-right text-sm text-success-foreground" : "max-w-md text-right text-sm text-warning-foreground"}
          data-testid="validez-resultado"
        >
          {resultado.texto}
        </p>
      ) : null}
    </div>
  );
}
