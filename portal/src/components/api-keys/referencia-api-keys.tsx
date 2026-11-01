"use client";

import { BookOpenIcon, DatabaseIcon, FingerprintIcon, LockIcon, ShieldCheckIcon } from "@phosphor-icons/react";
import Link from "next/link";
import { useMessages, useTranslations } from "next-intl";
import {
  type CampoReferencia,
  NotaReferencia,
  ReferenciaTecnicaDialog,
  ReglaReferencia,
  SeccionReferencia,
  TablaCampos,
} from "@/components/compartidos/referencia-tecnica";
import type { Mensajes } from "@/i18n/mensajes";

type DescripcionDeCampo = keyof Mensajes["apiKeys"]["referencia"]["campos"];

/** Las columnas de la tabla `api_key`; la descripción de cada una está en `messages.apiKeys.referencia.campos.<campo>`. */
const CAMPOS: Array<Omit<CampoReferencia, "descripcion"> & { campo: DescripcionDeCampo }> = [
  { campo: "id", tipo: "uuid", clave: true },
  { campo: "tenant_id", tipo: "uuid" },
  { campo: "key_hash", tipo: "varchar(64)" },
  { campo: "prefijo", tipo: "varchar(10)" },
  { campo: "activa", tipo: "boolean" },
  { campo: "created_at", tipo: "timestamptz" },
  { campo: "revoked_at", tipo: "timestamptz" },
];

const REGLAS = ["header", "formato", "alcance", "gestion", "revocacion"] as const;

/** Modelo de datos de la entidad 'api_key' y reglas de autenticación, como referencia en un modal. */
export function ReferenciaApiKeysDialog({ className }: { className?: string }) {
  const t = useMessages().apiKeys.referencia;
  const tr = useTranslations("apiKeys.referencia");
  const campos: CampoReferencia[] = CAMPOS.map((c) => ({ ...c, descripcion: t.campos[c.campo] }));
  return (
    <ReferenciaTecnicaDialog
      titulo={t.titulo}
      descripcion={t.descripcion}
      className={className}
      pie={
        <Link href="/developers" target="_blank" className="inline-flex items-center gap-1 text-[12px] font-medium text-primary hover:underline">
          <BookOpenIcon className="size-3.5" />
          {t.documentacion}
        </Link>
      }
    >
      <SeccionReferencia icono={DatabaseIcon} titulo={t.entidad} subtitulo={t.entidadSubtitulo}>
        <TablaCampos
          campos={campos}
          nota={
            <span className="inline-flex items-center gap-1">
              <LockIcon className="size-3" /> {t.notaTabla}
            </span>
          }
        />
      </SeccionReferencia>

      <SeccionReferencia icono={ShieldCheckIcon} titulo={t.reglasTitulo} subtitulo={t.reglasSubtitulo}>
        <div className="grid grid-cols-1 gap-2.5 p-4">
          {REGLAS.map((clave) => {
            const r = t.reglas[clave];
            return <ReglaReferencia key={clave} insignia={<FingerprintIcon className="size-4" />} titulo={r.titulo} detalle={r.detalle} valor={r.codigo} />;
          })}
        </div>
        <NotaReferencia>{tr.rich("nota", { b: (texto) => <strong className="font-medium text-foreground">{texto}</strong> })}</NotaReferencia>
      </SeccionReferencia>
    </ReferenciaTecnicaDialog>
  );
}
