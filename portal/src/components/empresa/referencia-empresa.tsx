"use client";

import { BookOpenIcon, DatabaseIcon, KeyRoundIcon, LockIcon, ShieldCheckIcon } from "lucide-react";
import { useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";

const CAMPOS: Array<{
  campo: string;
  tipo: string;
  descripcion: string;
  clave?: boolean;
}> = [
  {
    campo: "id",
    tipo: "uuid",
    descripcion: "Identificador del tenant; viaja en la cabecera X-Empresa",
    clave: true,
  },
  {
    campo: "ruc",
    tipo: "varchar(11)",
    descripcion: "Único en la plataforma; debe coincidir con el OU del certificado",
  },
  {
    campo: "razon_social",
    tipo: "varchar",
    descripcion: "Nombre legal tal como figura en la ficha RUC",
  },
  {
    campo: "entorno",
    tipo: "BETA | PRODUCCION",
    descripcion: "Endpoint SUNAT al que se envían los comprobantes",
  },
  {
    campo: "sol_usuario / sol_clave",
    tipo: "cifrado",
    descripcion: "Usuario secundario y clave SOL; se envían como RUC+usuario en el UsernameToken",
  },
  {
    campo: "certificado_pkcs12 / clave",
    tipo: "cifrado",
    descripcion: "Archivo .p12 y su contraseña, para firmar XML-DSig",
  },
  {
    campo: "certificado_vigencia_hasta",
    tipo: "date",
    descripcion: "notAfter del certificado, en hora de Lima",
  },
];

const REGLAS = [
  {
    titulo: "RUC en el certificado",
    texto:
      "Al cargar el .p12 se abre con la contraseña, se exige clave privada y se verifica que el RUC de la empresa figure en el campo OU del sujeto. Si no, se rechaza (CERTIFICADO_INVALIDO).",
  },
  {
    titulo: "Usuario SOL secundario",
    texto:
      "SUNAT exige un usuario secundario con permiso de emisión. El portal guarda solo el usuario; al enviar, el sistema antepone el RUC (RUC+USUARIO) en el UsernameToken del SOAP.",
  },
  {
    titulo: "Cifrado en reposo",
    texto:
      "Credenciales SOL, PKCS#12 y su contraseña se guardan cifrados con la clave maestra del servicio (MASTER_KEY). Nunca vuelven en claro por la API: por eso el portal no puede mostrarlos, solo reemplazarlos.",
  },
  {
    titulo: "Entorno",
    texto: "BETA apunta a la homologación de SUNAT (sin validez tributaria); Producción, al servicio real. Cada empresa fija el suyo al registrarse.",
  },
];

const SECCION = "rounded-xl border border-border/80 bg-card shadow-2xs";
const CABECERA = "flex flex-wrap items-center gap-x-2.5 gap-y-2 border-b border-border/60 px-4 py-3";
const ICONO = "flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary";
const TH = "px-4 py-2 text-left text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase";

export function ReferenciaEmpresaDialog({ className }: { className?: string }) {
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
              <DialogTitle className="text-base font-semibold tracking-tight">Referencia técnica de empresa</DialogTitle>
              <DialogDescription className="text-[13px]">Modelo de datos del tenant y reglas de validación de certificado y credenciales SOL</DialogDescription>
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
                  <h4 className="text-[13px] font-semibold text-foreground">Entidad &lsquo;tenant&rsquo; (empresa)</h4>
                  <span className="font-mono text-[11px] text-muted-foreground">una fila por razón social · pertenece a una cuenta</span>
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
                  <LockIcon className="size-3" /> Secretos cifrados con MASTER_KEY; nunca se exponen por la API
                </span>
              </div>
            </section>

            <section className={SECCION}>
              <div className={CABECERA}>
                <div className={ICONO}>
                  <ShieldCheckIcon className="size-4" />
                </div>
                <div className="flex min-w-0 flex-1 basis-48 flex-col">
                  <h4 className="text-[13px] font-semibold text-foreground">Reglas de validación</h4>
                  <span className="font-mono text-[11px] text-muted-foreground">lo que el sistema comprueba al guardar</span>
                </div>
              </div>
              <div className="grid grid-cols-1 gap-2.5 p-4">
                {REGLAS.map((r) => (
                  <div key={r.titulo} className="rounded-lg border border-border/60 bg-muted/40 px-3 py-2.5">
                    <p className="text-[13px] font-medium text-foreground">{r.titulo}</p>
                    <p className="mt-0.5 text-[12px] leading-relaxed text-muted-foreground">{r.texto}</p>
                  </div>
                ))}
              </div>
            </section>
          </div>
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-border/60 px-5 py-3">
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
