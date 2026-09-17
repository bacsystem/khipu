"use client";

import { BookOpenIcon, DatabaseIcon, KeyRoundIcon, LockIcon, NetworkIcon, ShieldCheckIcon } from "lucide-react";
import { useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { cn } from "@/lib/utils";

const CAMPOS: Array<{
  campo: string;
  tipo: string;
  descripcion: string;
  clave?: boolean;
}> = [
  {
    campo: "tenant_id",
    tipo: "uuid",
    descripcion: "Empresa emisora dueña de la serie",
    clave: true,
  },
  {
    campo: "tipo",
    tipo: "varchar(2)",
    descripcion: "Tipo de comprobante · catálogo SUNAT N.º 01",
    clave: true,
  },
  {
    campo: "codigo",
    tipo: "varchar(4)",
    descripcion: "Serie alfanumérica (F###, B###)",
    clave: true,
  },
  {
    campo: "ultimo_numero",
    tipo: "bigint",
    descripcion: "Último correlativo asignado; 0 = sin emisiones",
  },
  {
    campo: "activa",
    tipo: "boolean",
    descripcion: "Si acepta nuevas emisiones",
  },
];

const FORMATOS: Array<{
  tipo: string;
  nombre: string;
  formato: string;
  nota: string;
}> = [
  {
    tipo: "01",
    nombre: "Factura electrónica",
    formato: "F###",
    nota: "Inicia con F + 3 alfanuméricos",
  },
  {
    tipo: "03",
    nombre: "Boleta de venta",
    formato: "B###",
    nota: "Inicia con B + 3 alfanuméricos",
  },
  {
    tipo: "07",
    nombre: "Nota de crédito",
    formato: "F### / B###",
    nota: "Prefijo del comprobante que modifica",
  },
  {
    tipo: "08",
    nombre: "Nota de débito",
    formato: "F### / B###",
    nota: "Prefijo del comprobante que modifica",
  },
];

const SECCION = "rounded-xl border border-border/80 bg-card shadow-2xs";
const CABECERA = "flex flex-wrap items-center gap-x-2.5 gap-y-2 border-b border-border/60 px-4 py-3";
const ICONO = "flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary";
const TH = "px-4 py-2 text-left text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase";

/** Definición de la entidad y regla de formato SUNAT, como referencia en un modal. */
export function ReferenciaSeriesDialog({ className }: { className?: string }) {
  const [abierto, setAbierto] = useState(false);

  return (
    <Dialog open={abierto} onOpenChange={setAbierto}>
      <DialogTrigger
        className={
          className ??
          "inline-flex h-8 items-center gap-1.5 rounded-lg border border-border bg-card px-2.5 text-[12px] font-medium text-foreground/80 shadow-2xs transition-colors hover:bg-muted hover:text-foreground"
        }
      >
        <BookOpenIcon className="size-4" />
        Referencia técnica
      </DialogTrigger>

      <DialogContent
        className="gap-0 p-0"
        // Ancho por estilo inline: no depende de breakpoints ni del orden de las utilidades.
        style={{ maxWidth: "min(1200px, calc(100vw - 3rem))" }}
      >
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className={ICONO}>
              <BookOpenIcon className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">Referencia técnica de series</DialogTitle>
              <DialogDescription className="text-[13px]">Modelo de datos de la entidad &lsquo;serie&rsquo; y regla de formato SUNAT</DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="@container bg-muted/40 px-5 py-4">
          <div className="grid grid-cols-1 items-start gap-4 @3xl:grid-cols-2">
            <section className={SECCION}>
              <div className={CABECERA}>
                <div className={ICONO}>
                  <DatabaseIcon className="size-4" />
                </div>
                <div className="flex min-w-0 flex-1 basis-48 flex-col">
                  <h4 className="text-[13px] font-semibold text-foreground">Entidad &lsquo;serie&rsquo;</h4>
                  <span className="font-mono text-[11px] text-muted-foreground">tabla serie · una fila por empresa, tipo y código</span>
                </div>
                <button
                  disabled
                  title="Diagrama entidad-relación: próximamente"
                  className="inline-flex shrink-0 cursor-not-allowed items-center gap-1.5 rounded-lg border border-border bg-card px-2.5 py-1.5 font-mono text-[11px] whitespace-nowrap text-muted-foreground/60"
                >
                  <NetworkIcon className="size-3.5" />
                  Ver diagrama ER
                </button>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-[13px]">
                  <thead>
                    <tr className="border-b border-border/60 bg-muted">
                      <th className={TH}>Campo</th>
                      <th className={TH}>Tipo</th>
                      <th className={TH}>Descripción</th>
                    </tr>
                  </thead>
                  <tbody>
                    {CAMPOS.map((c) => (
                      <tr key={c.campo} className="border-b border-border/60 last:border-0">
                        <td className="px-4 py-2 whitespace-nowrap">
                          <span className="inline-flex items-center gap-1.5 font-mono font-semibold text-foreground">
                            {c.campo}
                            {c.clave ? <KeyRoundIcon className="size-3 text-warning-solid" aria-label="Parte de la clave única" /> : null}
                          </span>
                        </td>
                        <td className="px-4 py-2 whitespace-nowrap">
                          <span className="rounded bg-secondary px-1.5 py-0.5 font-mono text-[11px] font-medium text-primary">{c.tipo}</span>
                        </td>
                        <td className="px-4 py-2 text-muted-foreground">{c.descripcion}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-border/60 bg-muted/60 px-4 py-2 font-mono text-[11px] text-muted-foreground">
                <span className="inline-flex items-center gap-1">
                  <KeyRoundIcon className="size-3 text-warning-solid" /> Clave única: (tenant_id, tipo, codigo)
                </span>
                <span className="inline-flex items-center gap-1">
                  <LockIcon className="size-3" /> Numeración atómica: bloqueo de fila por emisión
                </span>
              </div>
            </section>

            <section className={SECCION}>
              <div className={CABECERA}>
                <div className={ICONO}>
                  <ShieldCheckIcon className="size-4" />
                </div>
                <div className="flex min-w-0 flex-1 basis-48 flex-col">
                  <h4 className="text-[13px] font-semibold text-foreground">Regla de formato y validación SUNAT</h4>
                  <span className="font-mono text-[11px] text-muted-foreground">
                    R.S. 097-2012/SUNAT · serie de 4 caracteres + correlativo de hasta 8 dígitos
                  </span>
                </div>
              </div>
              <div className="grid grid-cols-1 gap-2.5 p-4">
                {FORMATOS.map((f) => (
                  <div key={f.tipo} className="flex flex-wrap items-center gap-x-3 gap-y-1.5 rounded-lg border border-border/60 bg-muted/40 px-3 py-2.5">
                    <span className="flex size-8 shrink-0 items-center justify-center rounded bg-accent font-mono text-[11px] font-semibold text-primary">
                      {f.tipo}
                    </span>
                    <div className="flex min-w-0 flex-1 basis-40 flex-col">
                      <span className="text-[13px] font-medium text-foreground">{f.nombre}</span>
                      <span className="text-[11px] text-muted-foreground">{f.nota}</span>
                    </div>
                    <span className="shrink-0 rounded bg-card px-2 py-0.5 font-mono text-[12px] font-semibold text-foreground shadow-2xs">{f.formato}</span>
                  </div>
                ))}
              </div>
              <p className="border-t border-border/60 px-4 py-3 text-[12px] leading-relaxed text-muted-foreground">
                Las notas de crédito y débito usan el prefijo del comprobante que modifican (<strong className="font-medium text-foreground">F</strong> para
                facturas, <strong className="font-medium text-foreground">B</strong> para boletas). El correlativo se incrementa de forma secuencial y
                automática por cada XML emitido, sin saltos: si la firma o el almacenamiento fallan, el número no se consume.
              </p>
            </section>
          </div>
        </div>

        <div className={cn("flex items-center justify-end gap-2 border-t border-border/60 px-5 py-3")}>
          <button
            type="button"
            onClick={() => setAbierto(false)}
            className="inline-flex h-9 items-center rounded-lg bg-foreground px-3.5 text-[13px] font-medium text-background shadow-xs transition-colors hover:bg-foreground/90"
          >
            Entendido
          </button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
