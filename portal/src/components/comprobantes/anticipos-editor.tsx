"use client";

import { Campo } from "khipu-design-system/formularios/campo";
import { AFECTACIONES_ANTICIPO, anticipoInicial, errorDeAnticipos, type AnticipoForm } from "@/lib/comprobantes/anticipos";
import { AYUDA_CAMPO, CAMPO } from "@/lib/estilos";
import { formatearMonto } from "@/lib/formato";
import { cn } from "@/lib/utils";
import type { AnticiposDisponibles } from "./use-anticipos-disponibles";

const CAMPO_DENSO = cn(CAMPO, "text-[13px]");

/**
 * Descontar anticipos (#312): las facturas o boletas aceptadas del mismo cliente y moneda, cada una con lo que queda por descontar. Al marcar una se propone descontar todo
 * lo disponible; el valor (sin IGV) y la afectación se pueden cambiar. El total que se ve al pie ya es el que deja el descuento, calculado por el backend.
 */
export function AnticiposEditor({
  disponibles,
  elegidos,
  onCambio,
  moneda,
  hayCliente,
}: {
  disponibles: AnticiposDisponibles;
  elegidos: AnticipoForm[];
  onCambio: (a: AnticipoForm[]) => void;
  moneda: string;
  hayCliente: boolean;
}) {
  const error = errorDeAnticipos(elegidos, disponibles.candidatos);
  const cambiar = (i: number, cambio: Partial<AnticipoForm>) => onCambio(elegidos.map((a, j) => (j === i ? { ...a, ...cambio } : a)));
  return (
    <div className="grid gap-3" data-testid="anticipos-form">
      {!hayCliente ? (
        <p className={AYUDA_CAMPO}>Indique el documento del cliente para ver sus anticipos.</p>
      ) : disponibles.estado === "cargando" ? (
        <p className={AYUDA_CAMPO} role="status">Buscando anticipos del cliente…</p>
      ) : disponibles.estado === "error" ? (
        <p className={cn(AYUDA_CAMPO, "text-destructive")} role="alert">No se pudieron cargar los comprobantes. Cierre y vuelva a abrir esta sección para reintentar.</p>
      ) : disponibles.candidatos.length === 0 ? (
        <p className={AYUDA_CAMPO} data-testid="anticipos-vacio">
          Este cliente no tiene facturas ni boletas aceptadas en {moneda} con algo por descontar entre las últimas 100 emitidas.
        </p>
      ) : (
        <ul className="grid gap-2">
          {disponibles.candidatos.map((c) => {
            const i = elegidos.findIndex((a) => a.serie === c.serie && a.numero === c.numero);
            const elegido = i >= 0 ? elegidos[i] : null;
            return (
              <li key={c.etiqueta} className="grid gap-2 rounded-lg border border-border px-3 py-2">
                <label className="flex items-center gap-2 text-[13px]">
                  <input
                    type="checkbox"
                    checked={elegido !== null}
                    onChange={(e) => onCambio(e.target.checked ? [...elegidos, anticipoInicial(c)] : elegidos.filter((a) => !(a.serie === c.serie && a.numero === c.numero)))}
                    className="size-3.5 accent-primary"
                  />
                  <span className="font-mono font-medium">{c.etiqueta}</span>
                  <span className="text-muted-foreground">
                    {c.fecha} · por descontar {AFECTACIONES_ANTICIPO.filter((a) => c.disponible[a] > 0).map((a) => `${formatearMonto(moneda, c.disponible[a])} ${a}`).join(" · ")}
                  </span>
                </label>
                {elegido ? (
                  <div className="grid items-start gap-3 sm:grid-cols-[1fr_160px]">
                    <Campo id={`nc-ant-monto-${c.etiqueta}`} etiqueta={`Valor a descontar de ${c.etiqueta}`} ayuda="Sin IGV">
                      <input id={`nc-ant-monto-${c.etiqueta}`} value={elegido.monto} onChange={(e) => cambiar(i, { monto: e.target.value })} inputMode="decimal" className={cn(CAMPO_DENSO, "font-mono tabular-nums")} />
                    </Campo>
                    <Campo id={`nc-ant-afect-${c.etiqueta}`} etiqueta="Afectación">
                      <select
                        id={`nc-ant-afect-${c.etiqueta}`}
                        value={elegido.afectacion}
                        onChange={(e) => cambiar(i, { afectacion: e.target.value as AnticipoForm["afectacion"], monto: c.disponible[e.target.value as AnticipoForm["afectacion"]].toFixed(2) })}
                        className={CAMPO_DENSO}
                      >
                        {AFECTACIONES_ANTICIPO.filter((a) => c.disponible[a] > 0).map((a) => (
                          <option key={a} value={a}>{a}</option>
                        ))}
                      </select>
                    </Campo>
                  </div>
                ) : null}
              </li>
            );
          })}
        </ul>
      )}
      {error ? (
        <p role="alert" className={cn(AYUDA_CAMPO, "text-destructive")} data-testid="anticipos-error">
          {error}
        </p>
      ) : null}
    </div>
  );
}
