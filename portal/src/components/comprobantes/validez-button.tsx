"use client";

import { useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { apiRequest, noSeSabeSiLlego } from "@/lib/api/browser";
import { mensajeError } from "@/lib/messages";

type Validez = { estado: string; codigo: string | null; mensaje: string | null };

/** Lo que SUNAT contesta a `validaCDPcriterios`, dicho para el emisor. */
const ESTADOS: Record<string, string> = {
  ACEPTADO: "Aceptado por SUNAT (0001).",
  RECHAZADO: "SUNAT lo tiene como rechazado (0002).",
  DE_BAJA: "SUNAT lo tiene dado de baja (0003).",
  NO_EXISTE: "SUNAT no tiene este comprobante (0011): no llegó o los datos no coinciden.",
  AJENO: "SUNAT lo registra a nombre de otro emisor (0012).",
};

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

/** Consultar en SUNAT si el comprobante es válido (#315): `billValidService` por sus datos. Solo lee; no cambia el comprobante. */
export function ValidezButton({ datos }: { datos: DatosDeValidez }) {
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
      setResultado({ tono: "error", texto: "Se cortó la conexión mientras se consultaba a SUNAT. Reintente." });
      return;
    }
    if (res.estado !== "exito" || !res.datos) {
      setResultado({ tono: "error", texto: res.mensaje ?? mensajeError(res.codigo) });
      return;
    }
    const v = res.datos;
    const texto = ESTADOS[v.estado] ?? `SUNAT respondió ${v.codigo ?? ""} ${v.mensaje ?? ""}`.trim();
    setResultado({ tono: v.estado === "ACEPTADO" ? "ok" : v.estado === "ERROR_CONSULTA" ? "error" : "aviso", texto });
  }

  return (
    <div className="flex flex-col items-end gap-1.5">
      <Button variant="outline" disabled={consultando} onClick={consultar} data-testid="consultar-validez">
        {consultando ? "Consultando a SUNAT…" : "Consultar validez en SUNAT"}
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
