"use client";

import { CircleCheckIcon, ShieldCheckIcon, TriangleAlertIcon } from "lucide-react";
import Link from "next/link";
import { useRef, useState } from "react";
import { Etiqueta, type Tono } from "@/components/admin/etiquetas";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { hrefDetalleEmpresa } from "@/lib/api/admin-empresa-detalle";
import type { InformeDeIntegridad, TipoDeProblema } from "@/lib/api/admin-integridad";
import { apiRequest } from "@/lib/api/browser";
import { AYUDA_CAMPO, BOTON_PRIMARIO, CABECERA_TABLA, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { formatearFecha, sumarDias } from "@/lib/formato";
import { MAX_DIAS_DE_INTEGRIDAD, validarRango, type ErroresDeRango } from "@/lib/integridad-formulario";
import { messages, mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";

const t = messages.admin.integridad;

/** Un objeto perdido o alterado es grave (rojo); no poder leer el almacenamiento puede ser pasajero y pide repetir (aviso). */
const TONO: Record<TipoDeProblema, Tono> = { XML_FALTANTE: "error", XML_CORRUPTO: "error", CDR_FALTANTE: "error", STORAGE_INACCESIBLE: "aviso" };

type Fase = { estado: "inicial" } | { estado: "verificando" } | { estado: "listo"; informe: InformeDeIntegridad } | { estado: "error"; mensaje: string };

/** Las dos frases del resumen: cuántos se verificaron y qué se encontró. */
function resumen(i: InformeDeIntegridad): { verificados: string; hallazgo: string } {
  const r = t.resultado;
  const rango = (texto: string) => texto.replace("{desde}", formatearFecha(i.desde)).replace("{hasta}", formatearFecha(i.hasta));
  const verificados = i.verificados === 0 ? rango(r.verificadosNinguno) : i.verificados === 1 ? rango(r.verificadosUno) : rango(r.verificados).replace("{n}", String(i.verificados));
  const comprobantes = new Set(i.problemas.map((p) => p.comprobante_id)).size;
  const hallazgo =
    i.problemas.length === 0
      ? r.limpio
      : i.problemas.length === 1
        ? r.problemasUno
        : comprobantes === 1
          ? r.problemasEnUno.replace("{p}", String(i.problemas.length))
          : r.problemasVarios.replace("{p}", String(i.problemas.length)).replace("{c}", String(comprobantes));
  return { verificados, hallazgo };
}

/**
 * Lanzar y ver la verificación de integridad del almacenamiento (#198): que los XML y los CDR guardados siguen siendo los que se firmaron. El formulario pide el rango de
 * fechas de emisión (como mucho {@link MAX_DIAS_DE_INTEGRIDAD} días por vez); el resultado dice cuántos se verificaron y cuáles fallaron, cada uno con su enlace a la empresa.
 * Solo lee: no repara nada. No se reintenta sola ni se repite al volver a la página: cada barrido lo pide el administrador.
 */
export function VerificarIntegridad({ hoy }: { hoy: string }) {
  const [desde, setDesde] = useState(sumarDias(hoy, -6));
  const [hasta, setHasta] = useState(hoy);
  const [errores, setErrores] = useState<ErroresDeRango>({});
  const [fase, setFase] = useState<Fase>({ estado: "inicial" });
  // Un ref, no el estado: dos clics en el mismo tick leen el `fase` viejo del closure.
  const enviandoRef = useRef(false);

  async function verificar(e: React.FormEvent) {
    e.preventDefault();
    if (enviandoRef.current) return;
    const rango = validarRango(desde, hasta);
    if ("errores" in rango) {
      setErrores(rango.errores);
      return;
    }
    enviandoRef.current = true;
    setFase({ estado: "verificando" });
    const res = await apiRequest<InformeDeIntegridad>("/api/admin/integridad", { method: "POST", body: { desde: rango.desde, hasta: rango.hasta } });
    enviandoRef.current = false;
    if (res.estado === "exito" && res.datos) setFase({ estado: "listo", informe: res.datos });
    else setFase({ estado: "error", mensaje: res.mensaje ?? mensajeError(res.codigo) });
  }

  const verificando = fase.estado === "verificando";
  return (
    <div className="grid min-w-0 gap-4">
      <form onSubmit={verificar} noValidate className="flex flex-wrap items-start gap-3 rounded-xl border border-border/90 bg-card p-4 shadow-2xs">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="integridad-desde" className={ETIQUETA_CAMPO}>
            {t.desde}
          </label>
          <input
            id="integridad-desde"
            type="date"
            value={desde}
            onChange={(e) => {
              setDesde(e.target.value);
              setErrores({});
            }}
            aria-invalid={errores.desde ? true : undefined}
            className={cn(CAMPO, "h-9 w-44")}
          />
          {errores.desde ? <span className="text-[12px] text-destructive">{errores.desde}</span> : null}
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor="integridad-hasta" className={ETIQUETA_CAMPO}>
            {t.hasta}
          </label>
          <input
            id="integridad-hasta"
            type="date"
            value={hasta}
            onChange={(e) => {
              setHasta(e.target.value);
              setErrores({});
            }}
            aria-invalid={errores.hasta ? true : undefined}
            className={cn(CAMPO, "h-9 w-44")}
          />
          {errores.hasta ? <span className="text-[12px] text-destructive">{errores.hasta}</span> : <span className={AYUDA_CAMPO}>{t.ayudaRango.replace("{max}", String(MAX_DIAS_DE_INTEGRIDAD))}</span>}
        </div>
        <button type="submit" disabled={verificando} data-testid="integridad-verificar" className={cn(BOTON_PRIMARIO, "mt-[22px] h-9 px-3.5 text-[13px]")}>
          <ShieldCheckIcon className="size-4" />
          {verificando ? t.verificando : t.verificar}
        </button>
      </form>

      <div role="status" aria-live="polite" className="grid min-w-0 gap-4">
        {fase.estado === "verificando" ? (
          <p data-testid="integridad-aviso" className="rounded-lg border border-border bg-muted/50 px-3 py-2.5 text-[13px] text-muted-foreground">
            {t.avisoVerificando}
          </p>
        ) : null}
        {fase.estado === "error" ? (
          <p data-testid="integridad-error" role="alert" className="rounded-lg border border-destructive-border bg-destructive/10 px-3 py-2.5 text-sm text-destructive">
            {t.error} {fase.mensaje}
          </p>
        ) : null}
        {fase.estado === "listo" ? <Resultado informe={fase.informe} /> : null}
      </div>
    </div>
  );
}

function Resultado({ informe }: { informe: InformeDeIntegridad }) {
  const { verificados, hallazgo } = resumen(informe);
  const limpio = informe.problemas.length === 0;
  const tipos = [...new Set(informe.problemas.map((p) => p.tipo))];
  return (
    <section data-testid="integridad-resultado" data-limpio={limpio} className="grid min-w-0 gap-3">
      <div
        className={cn(
          "flex items-start gap-2.5 rounded-xl border px-4 py-3 text-sm",
          limpio ? "border-success-border bg-success text-success-foreground" : "border-warning-border bg-warning text-warning-foreground",
        )}
      >
        {limpio ? <CircleCheckIcon className="mt-0.5 size-4 shrink-0" /> : <TriangleAlertIcon className="mt-0.5 size-4 shrink-0" />}
        <div className="grid gap-0.5">
          <p data-testid="integridad-verificados">{verificados}</p>
          <p data-testid="integridad-hallazgo" className="font-medium">
            {hallazgo}
          </p>
        </div>
      </div>

      {limpio ? null : (
        <>
          <div className="overflow-x-auto rounded-xl border border-border/90 bg-card shadow-2xs">
            <Table>
              <TableHeader>
                <TableRow className="border-b border-border/80 bg-muted hover:bg-muted">
                  <TableHead className={`${CABECERA_TABLA} pl-4`}>{t.columnas.problema}</TableHead>
                  <TableHead className={CABECERA_TABLA}>{t.columnas.comprobante}</TableHead>
                  <TableHead className={CABECERA_TABLA}>{t.columnas.detalle}</TableHead>
                  <TableHead className={`${CABECERA_TABLA} pr-4`}>{t.columnas.empresa}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className="text-[13px]">
                {informe.problemas.map((p, i) => (
                  <TableRow key={`${p.comprobante_id}-${p.tipo}-${i}`} data-testid="integridad-problema" data-tipo={p.tipo} className="border-b border-border/60">
                    <TableCell className="py-2 pr-3 pl-4 align-top">
                      <Etiqueta tono={TONO[p.tipo]}>{t.tipos[p.tipo]}</Etiqueta>
                    </TableCell>
                    <TableCell className="px-3 py-2 align-top font-mono text-[12px] whitespace-nowrap">{p.nombre_archivo}</TableCell>
                    <TableCell className="px-3 py-2 align-top font-mono text-[11px] break-all text-muted-foreground">{p.detalle ?? "—"}</TableCell>
                    <TableCell className="py-2 pr-4 pl-3 align-top whitespace-nowrap">
                      <Link href={hrefDetalleEmpresa(p.tenant_id)} className="text-primary hover:underline">
                        {t.verEmpresa}
                      </Link>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <dl data-testid="integridad-leyenda" className="grid gap-1 text-[12px] text-muted-foreground">
            {tipos.map((tipo) => (
              <div key={tipo} className="flex flex-wrap gap-x-2">
                <dt className="font-medium text-foreground/80">{t.tipos[tipo]}:</dt>
                <dd>{t.tiposAyuda[tipo]}</dd>
              </div>
            ))}
          </dl>
        </>
      )}
    </section>
  );
}
