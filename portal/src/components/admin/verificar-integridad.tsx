"use client";

import { CheckCircleIcon, ShieldCheckIcon, WarningIcon } from "@phosphor-icons/react";
import { useLocale, useMessages } from "next-intl";
import Link from "next/link";
import { useRef, useState } from "react";
import { Etiqueta, type Tono } from "@/components/admin/etiquetas";
import { CONTENEDOR_TABLA } from "khipu-design-system/patrones/pie-tabla";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import { esIdDeComprobante, hrefDetalleComprobante } from "@/lib/api/admin-comprobante";
import { hrefDetalleEmpresa } from "@/lib/api/admin-empresa-detalle";
import type { InformeDeIntegridad, TipoDeProblema } from "@/lib/api/admin-integridad";
import { apiRequest } from "@/lib/api/browser";

/** El barrido lee cada XML y CDR del rango de todas las empresas (hasta 92 días): puede tardar bastante más que cualquier otra llamada; el límite del proxy es de 5 minutos. */
const ESPERA_DE_LA_VERIFICACION_MS = 300_000;
import { AYUDA_CAMPO, BOTON_PRIMARIO, CABECERA_TABLA, CAMPO, ETIQUETA_CAMPO, TARJETA } from "@/lib/estilos";
import { formatearFecha, sumarDias } from "@/lib/formato";
import { MAX_DIAS_DE_INTEGRIDAD, validarRango, type ErroresDeRango } from "@/lib/integridad-formulario";
import type { Locale } from "@/i18n/config";
import type { Mensajes } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { cn } from "@/lib/utils";

/** Un objeto perdido o alterado es grave (rojo); no poder leer el almacenamiento puede ser pasajero y pide repetir (aviso). */
const TONO: Record<TipoDeProblema, Tono> = { XML_FALTANTE: "error", XML_CORRUPTO: "error", CDR_FALTANTE: "error", STORAGE_INACCESIBLE: "aviso" };

type Fase = { estado: "inicial" } | { estado: "verificando" } | { estado: "listo"; informe: InformeDeIntegridad } | { estado: "error"; mensaje: string };

/** Las dos frases del resumen: cuántos se verificaron y qué se encontró. */
function resumen(i: InformeDeIntegridad, messages: Mensajes, locale: Locale): { verificados: string; hallazgo: string } {
  const r = messages.admin.integridad.resultado;
  const rango = (texto: string) => texto.replace("{desde}", formatearFecha(i.desde, locale)).replace("{hasta}", formatearFecha(i.hasta, locale));
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
  const messages = useMessages();
  const mensajeError = useMensajeError();
  const t = messages.admin.integridad;
  const [desde, setDesde] = useState(sumarDias(hoy, -6));
  const [hasta, setHasta] = useState(hoy);
  const [errores, setErrores] = useState<ErroresDeRango>({});
  const [fase, setFase] = useState<Fase>({ estado: "inicial" });
  // Un ref, no el estado: dos clics en el mismo tick leen el `fase` viejo del closure.
  const enviandoRef = useRef(false);

  /** El resultado es de un rango: si el rango cambia o no es válido, ya no corresponde a lo que dice el formulario (H22). Mientras verifica se deja. */
  function olvidarResultado() {
    setFase((f) => (f.estado === "verificando" ? f : { estado: "inicial" }));
  }

  async function verificar(e: React.FormEvent) {
    e.preventDefault();
    if (enviandoRef.current) return;
    const rango = validarRango(desde, hasta, messages);
    if ("errores" in rango) {
      olvidarResultado();
      setErrores(rango.errores);
      return;
    }
    enviandoRef.current = true;
    setFase({ estado: "verificando" });
    const res = await apiRequest<InformeDeIntegridad>("/api/admin/integridad", { method: "POST", body: { desde: rango.desde, hasta: rango.hasta }, esperaMaximaMs: ESPERA_DE_LA_VERIFICACION_MS });
    enviandoRef.current = false;
    if (res.estado === "exito" && res.datos) setFase({ estado: "listo", informe: res.datos });
    else setFase({ estado: "error", mensaje: res.mensaje ?? mensajeError(res.codigo) });
  }

  const verificando = fase.estado === "verificando";
  return (
    <div className="grid min-w-0 gap-4">
      <form onSubmit={verificar} noValidate className={cn(TARJETA, "flex flex-wrap items-start gap-3 p-4 shadow-xs")}>
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
              olvidarResultado();
              setErrores({});
            }}
            aria-invalid={errores.desde ? true : undefined}
            className={cn(CAMPO, "w-44")}
          />
          {errores.desde ? <span className="text-control text-destructive">{errores.desde}</span> : null}
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
              olvidarResultado();
              setErrores({});
            }}
            aria-invalid={errores.hasta ? true : undefined}
            className={cn(CAMPO, "w-44")}
          />
          {errores.hasta ? <span className="text-control text-destructive">{errores.hasta}</span> : <span className={AYUDA_CAMPO}>{t.ayudaRango.replace("{max}", String(MAX_DIAS_DE_INTEGRIDAD))}</span>}
        </div>
        <button type="submit" disabled={verificando} data-testid="integridad-verificar" className={cn(BOTON_PRIMARIO, "mt-6")}>
          <ShieldCheckIcon className="size-4" />
          {verificando ? t.verificando : t.verificar}
        </button>
      </form>

      <div role="status" aria-live="polite" className="grid min-w-0 gap-4">
        {fase.estado === "verificando" ? (
          <p data-testid="integridad-aviso" className="rounded-lg border border-border bg-muted/50 px-3 py-2.5 text-control text-muted-foreground">
            {t.avisoVerificando}
          </p>
        ) : null}
        {fase.estado === "error" ? (
          <p data-testid="integridad-error" role="alert" className="rounded-lg border border-destructive-border bg-destructive-suave px-3 py-2.5 text-cuerpo text-destructive-texto">
            {t.error} {fase.mensaje}
          </p>
        ) : null}
        {fase.estado === "listo" ? <Resultado informe={fase.informe} /> : null}
      </div>
    </div>
  );
}

function Resultado({ informe }: { informe: InformeDeIntegridad }) {
  const messages = useMessages();
  const locale = useLocale();
  const t = messages.admin.integridad;
  const { verificados, hallazgo } = resumen(informe, messages, locale);
  const limpio = informe.problemas.length === 0;
  const tipos = [...new Set(informe.problemas.map((p) => p.tipo))];
  return (
    <section data-testid="integridad-resultado" data-limpio={limpio} className="grid min-w-0 gap-3">
      <div
        className={cn(
          "flex items-start gap-2.5 rounded-xl border px-4 py-3 text-cuerpo",
          limpio ? "border-success-border bg-success text-success-foreground" : "border-warning-border bg-warning text-warning-foreground",
        )}
      >
        {limpio ? <CheckCircleIcon className="mt-0.5 size-4 shrink-0" /> : <WarningIcon className="mt-0.5 size-4 shrink-0" />}
        <div className="grid gap-0.5">
          <p data-testid="integridad-verificados">{verificados}</p>
          <p data-testid="integridad-hallazgo" className="font-medium">
            {hallazgo}
          </p>
        </div>
      </div>

      {limpio ? null : (
        <>
          <div className={CONTENEDOR_TABLA}>
            <Table>
              <TableHeader>
                <TableRow className="hover:bg-transparent">
                  <TableHead className={`${CABECERA_TABLA} pl-4`}>{t.columnas.problema}</TableHead>
                  <TableHead className={CABECERA_TABLA}>{t.columnas.comprobante}</TableHead>
                  <TableHead className={CABECERA_TABLA}>{t.columnas.detalle}</TableHead>
                  <TableHead className={`${CABECERA_TABLA} pr-4`}>{t.columnas.empresa}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className="text-control">
                {informe.problemas.map((p, i) => (
                  <TableRow key={`${p.comprobante_id}-${p.tipo}-${i}`} data-testid="integridad-problema" data-tipo={p.tipo}>
                    <TableCell className="py-2 pr-3 pl-4 align-top">
                      <Etiqueta tono={TONO[p.tipo]}>{t.tipos[p.tipo]}</Etiqueta>
                    </TableCell>
                    <TableCell className="px-3 py-2 align-top font-mono text-control whitespace-nowrap">
                      {/* #251: el fallo lleva a la ficha del comprobante; sin un id válido (un archivo suelto) queda solo el nombre. */}
                      {esIdDeComprobante(p.comprobante_id) ? (
                        <Link href={hrefDetalleComprobante(p.comprobante_id)} className="text-foreground hover:text-primary hover:underline">
                          {p.nombre_archivo}
                        </Link>
                      ) : (
                        p.nombre_archivo
                      )}
                    </TableCell>
                    <TableCell className="px-3 py-2 align-top font-mono text-etiqueta break-all text-muted-foreground">{p.detalle ?? "—"}</TableCell>
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
          <dl data-testid="integridad-leyenda" className="grid gap-1 text-control text-muted-foreground">
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
