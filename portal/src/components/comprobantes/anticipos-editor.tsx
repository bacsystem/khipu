"use client";

import { useLocale, useMessages } from "next-intl";
import { Campo } from "khipu-design-system/formularios/campo";
import { Casilla } from "khipu-design-system/formularios/casilla";
import { interpolar } from "@/i18n/mensajes";
import { AFECTACIONES_ANTICIPO, anticipoInicial, errorDeAnticipos, type AnticipoForm } from "@/lib/comprobantes/anticipos";
import { AYUDA_CAMPO, CAMPO, SELECT_NATIVO } from "@/lib/estilos";
import { formatearMonto } from "@/lib/formato";
import { cn } from "@/lib/utils";
import type { AnticiposDisponibles } from "./use-anticipos-disponibles";


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
  const messages = useMessages();
  const locale = useLocale();
  const t = messages.comprobantes.editores.anticipos;
  const afectacion = messages.documentos.afectacionAnticipo;
  const error = errorDeAnticipos(elegidos, disponibles.candidatos, messages);
  const cambiar = (i: number, cambio: Partial<AnticipoForm>) => onCambio(elegidos.map((a, j) => (j === i ? { ...a, ...cambio } : a)));
  return (
    <div className="grid gap-3" data-testid="anticipos-form">
      {!hayCliente ? (
        <p className={AYUDA_CAMPO}>{t.sinCliente}</p>
      ) : disponibles.estado === "cargando" ? (
        <p className={AYUDA_CAMPO} role="status">{t.cargando}</p>
      ) : disponibles.estado === "error" ? (
        <p className={cn(AYUDA_CAMPO, "text-destructive")} role="alert">{t.error}</p>
      ) : disponibles.candidatos.length === 0 ? (
        <p className={AYUDA_CAMPO} data-testid="anticipos-vacio">
          {interpolar(t.vacio, { moneda })}
        </p>
      ) : (
        <ul className="grid gap-2">
          {disponibles.candidatos.map((c) => {
            const i = elegidos.findIndex((a) => a.serie === c.serie && a.numero === c.numero);
            const elegido = i >= 0 ? elegidos[i] : null;
            return (
              <li key={c.etiqueta} className="grid gap-2 rounded-lg border border-border px-3 py-2">
                <Casilla
                  id={`nc-ant-${c.etiqueta}`}
                  etiqueta={<span className="font-mono">{c.etiqueta}</span>}
                  descripcion={interpolar(t.porDescontar, {
                    fecha: c.fecha,
                    montos: AFECTACIONES_ANTICIPO.filter((a) => c.disponible[a] > 0)
                      .map((a) => `${formatearMonto(moneda, c.disponible[a], locale)} ${afectacion[a]}`)
                      .join(" · "),
                  })}
                  checked={elegido !== null}
                  onCheckedChange={(v) => onCambio(v ? [...elegidos, anticipoInicial(c)] : elegidos.filter((a) => !(a.serie === c.serie && a.numero === c.numero)))}
                />
                {elegido ? (
                  <div className="grid items-start gap-3 sm:grid-cols-[1fr_160px]">
                    <Campo id={`nc-ant-monto-${c.etiqueta}`} etiqueta={interpolar(t.valorADescontar, { etiqueta: c.etiqueta })} ayuda={t.sinIgv}>
                      <input id={`nc-ant-monto-${c.etiqueta}`} value={elegido.monto} onChange={(e) => cambiar(i, { monto: e.target.value })} inputMode="decimal" className={cn(CAMPO, "font-mono tabular-nums")} />
                    </Campo>
                    <Campo id={`nc-ant-afect-${c.etiqueta}`} etiqueta={t.afectacion}>
                      <select
                        id={`nc-ant-afect-${c.etiqueta}`}
                        value={elegido.afectacion}
                        onChange={(e) => cambiar(i, { afectacion: e.target.value as AnticipoForm["afectacion"], monto: c.disponible[e.target.value as AnticipoForm["afectacion"]].toFixed(2) })}
                        className={cn(SELECT_NATIVO, "w-full")}
                      >
                        {AFECTACIONES_ANTICIPO.filter((a) => c.disponible[a] > 0).map((a) => (
                          <option key={a} value={a}>{afectacion[a]}</option>
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
