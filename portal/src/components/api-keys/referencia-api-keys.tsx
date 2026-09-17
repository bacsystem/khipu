"use client";

import { BookOpenIcon, DatabaseIcon, FingerprintIcon, KeyRoundIcon, LockIcon, ShieldCheckIcon } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";

const CAMPOS: Array<{ campo: string; tipo: string; descripcion: string; clave?: boolean }> = [
  { campo: "id", tipo: "uuid", descripcion: "Identificador de la llave (el que usa el endpoint de revocación)", clave: true },
  { campo: "tenant_id", tipo: "uuid", descripcion: "Empresa emisora dueña de la llave" },
  { campo: "key_hash", tipo: "varchar(64)", descripcion: "SHA-256 de la llave + pepper del servidor; nunca sale por la API" },
  { campo: "prefijo", tipo: "varchar(10)", descripcion: "Primeros 10 caracteres, para reconocerla en listados" },
  { campo: "activa", tipo: "boolean", descripcion: "false una vez revocada; la API responde 401 con esa llave" },
  { campo: "created_at", tipo: "timestamptz", descripcion: "Momento de creación" },
  { campo: "revoked_at", tipo: "timestamptz", descripcion: "Momento de revocación, nulo mientras esté activa" },
];

const REGLAS: Array<{ titulo: string; detalle: string; codigo: string }> = [
  { titulo: "Header de autenticación", detalle: "Una llave por request, sin esquema Bearer", codigo: "X-Api-Key" },
  { titulo: "Formato de la llave", detalle: "Prefijo fk_ + 40 caracteres aleatorios (base64url)", codigo: "fk_…" },
  { titulo: "Alcance", detalle: "Cada llave pertenece a una sola empresa (RUC); no hace falta X-Empresa", codigo: "tenant" },
  { titulo: "Revocación", detalle: "Irreversible: la llave deja de autenticar al instante, sin período de gracia", codigo: "DELETE" },
];

const SECCION = "rounded-xl border border-border/80 bg-card shadow-2xs";
const CABECERA = "flex flex-wrap items-center gap-x-2.5 gap-y-2 border-b border-border/60 px-4 py-3";
const ICONO = "flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary";
const TH = "px-4 py-2 text-left text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase";

/** Modelo de datos de la entidad 'api_key' y reglas de autenticación, como referencia en un modal. */
export function ReferenciaApiKeysDialog({ className }: { className?: string }) {
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

      <DialogContent className="gap-0 p-0" style={{ maxWidth: "min(1200px, calc(100vw - 3rem))" }}>
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className={ICONO}>
              <BookOpenIcon className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">Referencia técnica de API keys</DialogTitle>
              <DialogDescription className="text-[13px]">Modelo de datos de la entidad &lsquo;api_key&rsquo; y reglas de autenticación</DialogDescription>
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
                  <h4 className="text-[13px] font-semibold text-foreground">Entidad &lsquo;api_key&rsquo;</h4>
                  <span className="font-mono text-[11px] text-muted-foreground">tabla api_key · varias llaves por empresa</span>
                </div>
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
                            {c.clave ? <KeyRoundIcon className="size-3 text-warning-solid" aria-label="Clave primaria" /> : null}
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
                  <LockIcon className="size-3" /> key_hash único · la llave en claro no se almacena
                </span>
              </div>
            </section>

            <section className={SECCION}>
              <div className={CABECERA}>
                <div className={ICONO}>
                  <ShieldCheckIcon className="size-4" />
                </div>
                <div className="flex min-w-0 flex-1 basis-48 flex-col">
                  <h4 className="text-[13px] font-semibold text-foreground">Reglas de autenticación</h4>
                  <span className="font-mono text-[11px] text-muted-foreground">rutas /v1/facturas, /v1/series y /v1/empresa</span>
                </div>
              </div>
              <div className="grid grid-cols-1 gap-2.5 p-4">
                {REGLAS.map((r) => (
                  <div key={r.titulo} className="flex flex-wrap items-center gap-x-3 gap-y-1.5 rounded-lg border border-border/60 bg-muted/40 px-3 py-2.5">
                    <span className="flex size-8 shrink-0 items-center justify-center rounded bg-accent text-primary">
                      <FingerprintIcon className="size-4" />
                    </span>
                    <div className="flex min-w-0 flex-1 basis-40 flex-col">
                      <span className="text-[13px] font-medium text-foreground">{r.titulo}</span>
                      <span className="text-[11px] text-muted-foreground">{r.detalle}</span>
                    </div>
                    <span className="shrink-0 rounded bg-card px-2 py-0.5 font-mono text-[12px] font-semibold text-foreground shadow-2xs">{r.codigo}</span>
                  </div>
                ))}
              </div>
              <p className="border-t border-border/60 px-4 py-3 text-[12px] leading-relaxed text-muted-foreground">
                El portal no usa API keys: entra con tu sesión (JWT) y el header <strong className="font-medium text-foreground">X-Empresa</strong>. Las
                llaves son para sistemas externos que emiten por integración; una llave comprometida se revoca aquí y se reemplaza por una nueva.
              </p>
            </section>
          </div>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border/60 px-5 py-3">
          <Link href="/developers" target="_blank" className="inline-flex items-center gap-1 text-[12px] font-medium text-primary hover:underline">
            <BookOpenIcon className="size-3.5" />
            Documentación completa de la API
          </Link>
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
