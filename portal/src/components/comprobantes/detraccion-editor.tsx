"use client";

import Link from "next/link";
import { useLocale, useMessages } from "next-intl";
import { useEffect, useState } from "react";
import { Campo } from "khipu-design-system/formularios/campo";
import { SelectCampo } from "khipu-design-system/formularios/select-campo";
import { interpolar } from "@/i18n/mensajes";
import { apiRequest } from "@/lib/api/browser";
import { CODIGOS_SIN_FORMULARIO, errorDeDetraccion, PORCENTAJE_SUGERIDO, type DetraccionForm } from "@/lib/comprobantes/detraccion";
import { AYUDA_CAMPO, CAMPO } from "@/lib/estilos";
import { formatearMonto } from "@/lib/formato";
import { cn } from "@/lib/utils";


type Entrada = { codigo: string; descripcion: string };

/**
 * Detracción (SPOT, #288) de una factura: bien o servicio sujeto (catálogo 54), porcentaje (sugerido por SUNAT, editable), medio de pago (catálogo 59) y la cuenta del
 * Banco de la Nación, que viene de los datos fiscales de la empresa. El portal pregunta y avisa; el monto lo calcula el backend y se muestra en el pie.
 */
export function DetraccionEditor({
  valor,
  onCambio,
  moneda,
  montoCalculado,
  cuentaDeLaEmpresa,
}: {
  valor: DetraccionForm;
  onCambio: (d: DetraccionForm) => void;
  moneda: string;
  /** El monto a depositar en soles que calculó el backend, si ya respondió. */
  montoCalculado: number | null;
  cuentaDeLaEmpresa: string | null;
}) {
  const messages = useMessages();
  const locale = useLocale();
  const t = messages.comprobantes.editores;
  const [bienes, setBienes] = useState<Entrada[] | null>(null);
  const [medios, setMedios] = useState<Entrada[] | null>(null);
  const [busqueda, setBusqueda] = useState("");
  useEffect(() => {
    let vigente = true;
    const cargar = (codigo: string, poner: (e: Entrada[]) => void) =>
      apiRequest<{ entradas: Entrada[] }>(`/api/proxy/catalogos/${codigo}`, { method: "GET" })
        .then((res) => vigente && poner(res.estado === "exito" && res.datos ? res.datos.entradas : []))
        .catch(() => vigente && poner([]));
    cargar("54", setBienes);
    cargar("59", setMedios);
    return () => {
      vigente = false;
    };
  }, []);
  const error = errorDeDetraccion(valor, moneda, messages);
  // El catálogo 54 tiene unas 40 entradas: se filtra por código o por palabra, y el elegido nunca desaparece de la lista.
  const texto = busqueda.trim().toLowerCase();
  const ofrecidos = (bienes ?? []).filter((b) => !CODIGOS_SIN_FORMULARIO.has(b.codigo) && (b.codigo === valor.codigo || `${b.codigo} ${b.descripcion}`.toLowerCase().includes(texto)));
  const set = (cambio: Partial<DetraccionForm>) => onCambio({ ...valor, ...cambio });
  return (
    <div className="grid gap-3" data-testid="detraccion-form">
      <div className="grid items-start gap-4 sm:grid-cols-[1fr_140px]">
        <Campo id="nc-det-codigo" etiqueta={t.detraccion.bien} ayuda={t.detraccion.catalogo54}>
          <input
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            aria-label={t.detraccion.buscarEtiqueta}
            placeholder={t.detraccion.buscarPlaceholder}
            className={cn(CAMPO, "mb-1.5")}
          />
          <SelectCampo
            id="nc-det-codigo"
            valor={valor.codigo}
            onCambio={(v) => set({ codigo: v, porcentaje: PORCENTAJE_SUGERIDO[v] !== undefined ? String(PORCENTAJE_SUGERIDO[v]) : valor.porcentaje })}
            opciones={[{ value: "", label: t.detraccion.seleccione }, ...ofrecidos.map((b) => ({ value: b.codigo, label: `${b.codigo} · ${b.descripcion}` }))]}
          />
        </Campo>
        <Campo id="nc-det-porcentaje" etiqueta={t.detraccion.porcentaje} ayuda={PORCENTAJE_SUGERIDO[valor.codigo] !== undefined ? t.detraccion.sugerido : t.detraccion.segunAnexo}>
          <input
            id="nc-det-porcentaje"
            value={valor.porcentaje}
            onChange={(e) => set({ porcentaje: e.target.value })}
            inputMode="decimal"
            placeholder="12"
            className={cn(CAMPO, "font-mono tabular-nums")}
          />
        </Campo>
      </div>
      <div className="grid items-start gap-4 sm:grid-cols-2">
        <Campo id="nc-det-medio" etiqueta={t.detraccion.medioPago} ayuda={t.detraccion.catalogo59}>
          <SelectCampo
            id="nc-det-medio"
            valor={valor.medioPago}
            onCambio={(v) => set({ medioPago: v })}
            opciones={
              (medios ?? []).length === 0
                ? [{ value: valor.medioPago, label: interpolar(t.detraccion.depositoEnCuenta, { codigo: valor.medioPago }) }]
                : (medios ?? []).map((m) => ({ value: m.codigo, label: `${m.codigo} · ${m.descripcion}` }))
            }
          />
        </Campo>
        <Campo
          id="nc-det-cuenta"
          etiqueta={t.detraccion.cuenta}
          ayuda={
            cuentaDeLaEmpresa ? (
              t.detraccion.cuentaDeLaEmpresa
            ) : (
              <>
                {t.detraccion.faltaCuenta}{" "}
                <Link href="/empresa?seccion=datos" className="text-primary hover:underline">
                  {t.detraccion.configurarla}
                </Link>
              </>
            )
          }
        >
          <input id="nc-det-cuenta" value={valor.cuenta} onChange={(e) => set({ cuenta: e.target.value })} placeholder="00-000-123456" className={cn(CAMPO, "font-mono")} />
        </Campo>
      </div>
      {moneda !== "PEN" ? (
        <Campo id="nc-det-monto" etiqueta={t.detraccion.montoEnSoles} ayuda={interpolar(t.detraccion.montoEnSolesAyuda, { moneda })}>
          <input id="nc-det-monto" value={valor.montoEnSoles} onChange={(e) => set({ montoEnSoles: e.target.value })} inputMode="decimal" placeholder="0.00" className={cn(CAMPO, "font-mono tabular-nums sm:max-w-[200px]")} />
        </Campo>
      ) : null}
      <p className={cn(AYUDA_CAMPO, error ? "text-destructive" : undefined)} role={error ? "alert" : "status"} data-testid="detraccion-resumen">
        {error ?? (montoCalculado !== null ? interpolar(t.detraccion.resumen, { monto: formatearMonto("PEN", montoCalculado, locale) }) : t.montoAlEmitir)}
      </p>
    </div>
  );
}
