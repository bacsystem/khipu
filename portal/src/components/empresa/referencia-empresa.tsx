"use client";

import { DatabaseIcon, LockIcon, ShieldCheckIcon } from "lucide-react";
import { useMessages } from "next-intl";
import { type CampoReferencia, ReferenciaTecnicaDialog, SeccionReferencia, TablaCampos } from "@/components/compartidos/referencia-tecnica";

/** Las reglas que se muestran, en orden; título y texto vienen de `messages.empresa.referencia.reglas`. */
const REGLAS = ["rucCertificado", "usuarioSol", "cifrado", "entorno"] as const;

export function ReferenciaEmpresaDialog({ className }: { className?: string }) {
  const t = useMessages().empresa.referencia;
  const campos: CampoReferencia[] = [
    { campo: "id", tipo: "uuid", descripcion: t.campos.id, clave: true },
    { campo: "ruc", tipo: "varchar(11)", descripcion: t.campos.ruc },
    { campo: "razon_social", tipo: "varchar", descripcion: t.campos.razonSocial },
    { campo: "entorno", tipo: "BETA | PRODUCCION", descripcion: t.campos.entorno },
    { campo: "sol_usuario / sol_clave", tipo: t.tipoCifrado, descripcion: t.campos.sol },
    { campo: "certificado_pkcs12 / clave", tipo: t.tipoCifrado, descripcion: t.campos.certificado },
    { campo: "certificado_vigencia_hasta", tipo: "date", descripcion: t.campos.vigencia },
  ];
  return (
    <ReferenciaTecnicaDialog titulo={t.titulo} descripcion={t.descripcion} className={className}>
      <SeccionReferencia icono={DatabaseIcon} titulo={t.entidad} subtitulo={t.entidadSubtitulo}>
        <TablaCampos
          campos={campos}
          nota={
            <span className="inline-flex items-center gap-1">
              <LockIcon className="size-3" /> {t.nota}
            </span>
          }
        />
      </SeccionReferencia>

      <SeccionReferencia icono={ShieldCheckIcon} titulo={t.reglasTitulo} subtitulo={t.reglasSubtitulo}>
        <div className="grid grid-cols-1 gap-2.5 p-4">
          {REGLAS.map((id) => (
            <div key={id} className="rounded-lg border border-border/60 bg-muted/40 px-3 py-2.5">
              <p className="text-[13px] font-medium text-foreground">{t.reglas[id].titulo}</p>
              <p className="mt-0.5 text-[12px] leading-relaxed text-muted-foreground">{t.reglas[id].texto}</p>
            </div>
          ))}
        </div>
      </SeccionReferencia>
    </ReferenciaTecnicaDialog>
  );
}
