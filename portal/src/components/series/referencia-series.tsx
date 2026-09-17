"use client";

import { DatabaseIcon, KeyRoundIcon, LockIcon, NetworkIcon, ShieldCheckIcon } from "lucide-react";
import {
  type CampoReferencia,
  NotaReferencia,
  ReferenciaTecnicaDialog,
  ReglaReferencia,
  SeccionReferencia,
  TablaCampos,
} from "@/components/ui/referencia-tecnica";

const CAMPOS: CampoReferencia[] = [
  { campo: "tenant_id", tipo: "uuid", descripcion: "Empresa emisora dueña de la serie", clave: true },
  { campo: "tipo", tipo: "varchar(2)", descripcion: "Tipo de comprobante · catálogo SUNAT N.º 01", clave: true },
  { campo: "codigo", tipo: "varchar(4)", descripcion: "Serie alfanumérica (F###, B###)", clave: true },
  { campo: "ultimo_numero", tipo: "bigint", descripcion: "Último correlativo asignado; 0 = sin emisiones" },
  { campo: "activa", tipo: "boolean", descripcion: "Si acepta nuevas emisiones" },
];

const FORMATOS: Array<{ tipo: string; nombre: string; formato: string; nota: string }> = [
  { tipo: "01", nombre: "Factura electrónica", formato: "F###", nota: "Inicia con F + 3 alfanuméricos" },
  { tipo: "03", nombre: "Boleta de venta", formato: "B###", nota: "Inicia con B + 3 alfanuméricos" },
  { tipo: "07", nombre: "Nota de crédito", formato: "F### / B###", nota: "Prefijo del comprobante que modifica" },
  { tipo: "08", nombre: "Nota de débito", formato: "F### / B###", nota: "Prefijo del comprobante que modifica" },
];

/** Definición de la entidad y regla de formato SUNAT, como referencia en un modal. */
export function ReferenciaSeriesDialog({ className }: { className?: string }) {
  return (
    <ReferenciaTecnicaDialog
      titulo="Referencia técnica de series"
      descripcion={<>Modelo de datos de la entidad &lsquo;serie&rsquo; y regla de formato SUNAT</>}
      className={className}
    >
      <SeccionReferencia
        icono={DatabaseIcon}
        titulo={<>Entidad &lsquo;serie&rsquo;</>}
        subtitulo="tabla serie · una fila por empresa, tipo y código"
        accion={
          <button
            disabled
            title="Diagrama entidad-relación: próximamente"
            className="inline-flex shrink-0 cursor-not-allowed items-center gap-1.5 rounded-lg border border-border bg-card px-2.5 py-1.5 font-mono text-[11px] whitespace-nowrap text-muted-foreground/60"
          >
            <NetworkIcon className="size-3.5" />
            Ver diagrama ER
          </button>
        }
      >
        <TablaCampos
          campos={CAMPOS}
          etiquetaClave="Parte de la clave única"
          nota={
            <>
              <span className="inline-flex items-center gap-1">
                <KeyRoundIcon className="size-3 text-warning-solid" /> Clave única: (tenant_id, tipo, codigo)
              </span>
              <span className="inline-flex items-center gap-1">
                <LockIcon className="size-3" /> Numeración atómica: bloqueo de fila por emisión
              </span>
            </>
          }
        />
      </SeccionReferencia>

      <SeccionReferencia
        icono={ShieldCheckIcon}
        titulo="Regla de formato y validación SUNAT"
        subtitulo="R.S. 097-2012/SUNAT · serie de 4 caracteres + correlativo de hasta 8 dígitos"
      >
        <div className="grid grid-cols-1 gap-2.5 p-4">
          {FORMATOS.map((f) => (
            <ReglaReferencia key={f.tipo} insignia={f.tipo} titulo={f.nombre} detalle={f.nota} valor={f.formato} />
          ))}
        </div>
        <NotaReferencia>
          Las notas de crédito y débito usan el prefijo del comprobante que modifican (<strong className="font-medium text-foreground">F</strong> para
          facturas, <strong className="font-medium text-foreground">B</strong> para boletas). El correlativo se incrementa de forma secuencial y automática
          por cada XML emitido, sin saltos: si la firma o el almacenamiento fallan, el número no se consume.
        </NotaReferencia>
      </SeccionReferencia>
    </ReferenciaTecnicaDialog>
  );
}
