"use client";

import { DatabaseIcon, KeyIcon, LockIcon, ShieldCheckIcon } from "@phosphor-icons/react";
import { useMessages, useTranslations } from "next-intl";
import {
  type CampoReferencia,
  NotaReferencia,
  ReferenciaTecnicaDialog,
  ReglaReferencia,
  SeccionReferencia,
  TablaCampos,
} from "@/components/compartidos/referencia-tecnica";

// Las descripciones de cada campo y el nombre y la nota de cada formato están en `series.referencia` (campos / formatos).
const CAMPOS = [
  { campo: "tenant_id", tipo: "uuid", clave: true },
  { campo: "tipo", tipo: "varchar(2)", clave: true },
  { campo: "codigo", tipo: "varchar(4)", clave: true },
  { campo: "ultimo_numero", tipo: "bigint" },
  { campo: "activa", tipo: "boolean" },
] as const;

const FORMATOS = [
  { tipo: "01", formato: "F###" },
  { tipo: "03", formato: "B###" },
  { tipo: "07", formato: "F### / B###" },
  { tipo: "08", formato: "F### / B###" },
  { tipo: "09", formato: "T###" },
  { tipo: "31", formato: "V###" },
] as const;

/** Definición de la entidad y regla de formato SUNAT, como referencia en un modal. */
export function ReferenciaSeriesDialog({ className }: { className?: string }) {
  const t = useMessages().series.referencia;
  const tr = useTranslations("series.referencia");
  const campos: CampoReferencia[] = CAMPOS.map((c) => ({ ...c, descripcion: t.campos[c.campo] }));
  return (
    <ReferenciaTecnicaDialog
      titulo={t.titulo}
      descripcion={t.descripcion}
      className={className}
    >
      <SeccionReferencia
        icono={DatabaseIcon}
        titulo={t.entidad}
        subtitulo={t.subtituloEntidad}
      >
        <TablaCampos
          campos={campos}
          etiquetaClave={t.etiquetaClave}
          nota={
            <>
              <span className="inline-flex items-center gap-1">
                <KeyIcon className="size-3 text-warning-solid" /> {t.claveUnica}
              </span>
              <span className="inline-flex items-center gap-1">
                <LockIcon className="size-3" /> {t.numeracion}
              </span>
            </>
          }
        />
      </SeccionReferencia>

      <SeccionReferencia
        icono={ShieldCheckIcon}
        titulo={t.tituloRegla}
        subtitulo={t.subtituloRegla}
      >
        <div className="grid grid-cols-1 gap-2.5 p-4">
          {FORMATOS.map((f) => (
            <ReglaReferencia key={f.tipo} insignia={f.tipo} titulo={t.formatos[f.tipo].nombre} detalle={t.formatos[f.tipo].nota} valor={f.formato} />
          ))}
        </div>
        <NotaReferencia>
          {tr.rich("nota", { b: (c) => <strong className="font-medium text-foreground">{c}</strong> })}
        </NotaReferencia>
      </SeccionReferencia>
    </ReferenciaTecnicaDialog>
  );
}
