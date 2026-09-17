import {
  BadgeCheckIcon,
  Building2Icon,
  CheckIcon,
  CloudIcon,
  ExternalLinkIcon,
  FileKey2Icon,
  HelpCircleIcon,
  KeyRoundIcon,
  ShieldAlertIcon,
} from "lucide-react";
import { redirect } from "next/navigation";
import { CertificadoForm } from "@/components/empresa/certificado-form";
import { CredencialesSolForm } from "@/components/empresa/credenciales-sol-form";
import { NuevaEmpresaDialog } from "@/components/empresa/nueva-empresa-dialog";
import { listarEmpresas, obtenerEmpresaActual } from "@/lib/api/empresas";
import { ETIQUETA_DATO, TARJETA, TITULO_SECCION } from "@/lib/estilos";
import { formatearFecha } from "@/lib/formato";
import { getServerSession } from "@/lib/session-server";
import { cn } from "@/lib/utils";
import { Metrica } from "@/components/ui/metrica";

const DIA_MS = 86_400_000;

/** Días entre hoy (Lima) y una fecha ISO; negativo si ya pasó. */
function diasHasta(iso: string): number {
  const hoy = new Date(new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" }));
  return Math.round((new Date(iso).getTime() - hoy.getTime()) / DIA_MS);
}

function Punto({ tono }: { tono: "ok" | "aviso" | "error" | "neutro" }) {
  return (
    <span
      className={cn(
        "size-1.5 shrink-0 rounded-full",
        tono === "ok" && "bg-success-solid",
        tono === "aviso" && "bg-warning-solid",
        tono === "error" && "bg-destructive",
        tono === "neutro" && "bg-muted-foreground/50",
      )}
    />
  );
}

function Dato({ etiqueta, children, pendiente }: { etiqueta: string; children?: React.ReactNode; pendiente?: string }) {
  return (
    <div className="min-w-0" title={pendiente}>
      <span className={cn(ETIQUETA_DATO, "mb-1")}>{etiqueta}</span>
      {pendiente ? (
        <span className="inline-flex h-9 w-full cursor-not-allowed items-center rounded-lg border border-border/60 bg-muted px-3 text-[13px] text-muted-foreground/60">
          —
        </span>
      ) : (
        <span className="inline-flex h-9 w-full items-center rounded-lg border border-border bg-muted px-3 font-mono text-[13px] font-medium text-foreground">
          {children}
        </span>
      )}
    </div>
  );
}

function Pill({ tono, children }: { tono: "ok" | "aviso" | "neutro"; children: React.ReactNode }) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 font-mono text-[11px] font-medium whitespace-nowrap",
        tono === "ok" && "border-success-border bg-success text-success-foreground",
        tono === "aviso" && "border-warning-border bg-warning text-warning-foreground",
        tono === "neutro" && "border-border bg-secondary text-muted-foreground",
      )}
    >
      <Punto tono={tono} />
      {children}
    </span>
  );
}

export default async function EmpresaPage() {
  const { access, empresaId } = await getServerSession();
  if (!access) redirect("/login");
  if (!empresaId) redirect("/onboarding");

  const [empresa, empresas] = await Promise.all([obtenerEmpresaActual(access, empresaId), listarEmpresas(access)]);

  const beta = empresa.entorno === "BETA";
  const vigencia = empresa.certificado_vigencia_hasta;
  const dias = vigencia ? diasHasta(vigencia) : null;
  const estadoCert: {
    tono: "ok" | "aviso" | "error" | "neutro";
    texto: string;
  } =
    dias === null
      ? { tono: "neutro", texto: "Sin certificado" }
      : dias < 0
        ? { tono: "error", texto: "Vencido" }
        : dias <= 30
          ? { tono: "aviso", texto: "Por vencer" }
          : { tono: "ok", texto: "Vigente" };

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <section className="grid grid-cols-2 gap-x-5 gap-y-4 rounded-xl border border-border bg-card px-5 py-3 shadow-xs lg:grid-cols-4 lg:divide-x lg:divide-border lg:[&>*:not(:first-child)]:pl-5">
        <Metrica
          etiqueta="Estado del certificado"
          ayuda={
            vigencia ? (
              <>
                <span className="truncate">Vence el {formatearFecha(vigencia)}</span>
                <span className="text-muted-foreground/40">·</span>
                <span className={cn("shrink-0 font-mono", dias !== null && dias < 0 && "text-destructive")}>
                  {dias !== null && dias < 0 ? `hace ${Math.abs(dias)} días` : `${dias} días restantes`}
                </span>
              </>
            ) : (
              "Carga tu .p12 para poder firmar"
            )
          }
        >
          <span
            className={cn(
              estadoCert.tono === "ok" && "text-success-foreground",
              estadoCert.tono === "aviso" && "text-warning-foreground",
              estadoCert.tono === "error" && "text-destructive",
              estadoCert.tono === "neutro" && "text-muted-foreground/60",
            )}
          >
            {estadoCert.texto.toUpperCase()}
          </span>
          <span className="text-[12px] font-normal text-muted-foreground">PKCS#12</span>
        </Metrica>

        <Metrica
          etiqueta="Credenciales SOL"
          ayuda={
            <span className={cn("flex items-center gap-1", empresa.tiene_credenciales_sol ? "text-success-foreground" : "text-warning-foreground")}>
              <Punto tono={empresa.tiene_credenciales_sol ? "ok" : "aviso"} />
              {empresa.tiene_credenciales_sol ? "Usuario secundario configurado" : "Pendientes de configurar"}
            </span>
          }
        >
          <span className={empresa.tiene_credenciales_sol ? "text-primary" : "text-muted-foreground/60"}>
            {empresa.tiene_credenciales_sol ? "CONFIGURADAS" : "—"}
          </span>
        </Metrica>

        <Metrica
          etiqueta="Entorno SUNAT"
          ayuda={
            <>
              <span className="truncate">RUC {empresa.ruc}</span>
              <span className="text-muted-foreground/40">·</span>
              <span className="shrink-0 text-primary">UBL 2.1</span>
            </>
          }
        >
          <span className={beta ? "text-warning-foreground" : "text-foreground"}>{beta ? "BETA" : "PRODUCCIÓN"}</span>
          <span className="text-[12px] font-normal text-muted-foreground">{beta ? "homologación" : "validez tributaria"}</span>
        </Metrica>

        <Metrica
          etiqueta="Empresas en la cuenta"
          ayuda={
            <>
              <span className="truncate">{empresas.filter((e) => e.tiene_certificado && e.tiene_credenciales_sol).length} listas para emitir</span>
            </>
          }
        >
          {empresas.length}
          <span className="text-[12px] font-normal text-muted-foreground">{empresas.length === 1 ? "registrada" : "registradas"}</span>
        </Metrica>
      </section>

      <div className="grid grid-cols-1 items-start gap-4 lg:grid-cols-12">
        <div className="grid min-w-0 grid-cols-1 gap-4 lg:col-span-7">
          <section className={cn(TARJETA, "p-5")}>
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
              <div className={TITULO_SECCION}>
                <Building2Icon className="size-4" />
                Datos de la empresa (razón social y domicilio)
              </div>
              <span title="Estado del contribuyente en SUNAT: próximamente (requiere consulta RUC)">
                <Pill tono="neutro">Estado SUNAT: —</Pill>
              </span>
            </div>
            <div className="grid grid-cols-1 gap-4 pt-4 sm:grid-cols-2">
              <Dato etiqueta="RUC registrado">
                {empresa.ruc}
                <BadgeCheckIcon className="ml-auto size-4 text-success-solid" aria-label="RUC validado" />
              </Dato>
              <Dato etiqueta="Régimen tributario" pendiente="Régimen tributario: próximamente (requiere consulta RUC)" />
              <div className="sm:col-span-2">
                <Dato etiqueta="Razón social">{empresa.razon_social}</Dato>
              </div>
              <div className="sm:col-span-2">
                <Dato etiqueta="Domicilio fiscal registrado" pendiente="Domicilio fiscal: próximamente (requiere consulta RUC)" />
              </div>
            </div>
            <div className="mt-4 flex flex-wrap items-center justify-between gap-2 border-t border-border/60 pt-3 font-mono text-[11px] text-muted-foreground">
              <span className="inline-flex items-center gap-1.5 opacity-60" title="Envío directo a SUNAT; integración con OSE: próximamente">
                <CloudIcon className="size-3.5" />
                OSE asignado: — (envío directo a SUNAT)
              </span>
              <span className="opacity-60" title="Código de establecimiento anexo: próximamente">
                Cód. local domicilio: —
              </span>
            </div>
          </section>

          <section className={cn(TARJETA, "p-5")}>
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
              <div className={TITULO_SECCION}>
                <KeyRoundIcon className="size-4" />
                Credenciales SUNAT SOL (emisión)
              </div>
              <Pill tono={empresa.tiene_credenciales_sol ? "ok" : "aviso"}>{empresa.tiene_credenciales_sol ? "Configuradas" : "Pendientes"}</Pill>
            </div>
            <div className="mt-4 flex items-start gap-2.5 rounded-lg border border-accent-border bg-accent/60 p-3">
              <ShieldAlertIcon className="mt-0.5 size-4 shrink-0 text-accent-foreground" />
              <p className="text-[12px] leading-relaxed text-accent-foreground">
                SUNAT exige usar un <strong className="font-semibold">usuario secundario</strong> con permisos de emisión de comprobantes de pago. Nunca
                utilices las credenciales principales de tu Clave SOL maestra.
              </p>
            </div>
            <div className="mt-4">
              <CredencialesSolForm configuradas={empresa.tiene_credenciales_sol} />
            </div>
          </section>

          <section className={cn(TARJETA, "p-5")}>
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
              <div className={TITULO_SECCION}>
                <Building2Icon className="size-4" />
                Multi-empresa
              </div>
              <span className="font-mono text-[11px] text-muted-foreground">
                {empresas.length} {empresas.length === 1 ? "empresa" : "empresas"}
              </span>
            </div>
            <p className="mt-3 text-[12px] leading-relaxed text-muted-foreground">
              Asocia razones sociales complementarias bajo la misma cuenta. Cada empresa tiene sus propias series, certificado y credenciales; cambia la activa
              desde el selector del menú lateral.
            </p>
            <ul className="mt-3 divide-y divide-border/60 overflow-hidden rounded-lg border border-border/80">
              {empresas.map((e) => {
                const activa = e.id === empresaId;
                return (
                  <li key={e.id} className={cn("flex items-center gap-3 px-3 py-2.5", activa && "bg-accent/40")}>
                    <div
                      className={cn(
                        "flex size-8 shrink-0 items-center justify-center rounded-md font-mono text-[11px] font-semibold",
                        activa ? "bg-primary text-primary-foreground" : "bg-secondary text-muted-foreground",
                      )}
                    >
                      {e.razon_social.slice(0, 2).toUpperCase()}
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-[13px] font-medium text-foreground">{e.razon_social}</p>
                      <p className="font-mono text-[11px] text-muted-foreground">
                        RUC {e.ruc} · {e.entorno === "BETA" ? "Beta" : "Producción"}
                      </p>
                    </div>
                    <div className="flex shrink-0 items-center gap-1.5">
                      <span title={e.tiene_certificado ? "Certificado cargado" : "Sin certificado"}>
                        <FileKey2Icon className={cn("size-4", e.tiene_certificado ? "text-success-solid" : "text-muted-foreground/40")} />
                      </span>
                      <span title={e.tiene_credenciales_sol ? "Credenciales SOL configuradas" : "Sin credenciales SOL"}>
                        <KeyRoundIcon className={cn("size-4", e.tiene_credenciales_sol ? "text-success-solid" : "text-muted-foreground/40")} />
                      </span>
                      {activa ? (
                        <span className="ml-1 inline-flex items-center gap-1 rounded-full bg-primary/10 px-2 py-0.5 font-mono text-[10px] font-medium text-primary">
                          <CheckIcon className="size-3" /> Activa
                        </span>
                      ) : null}
                    </div>
                  </li>
                );
              })}
            </ul>
            <div className="mt-3 flex justify-end">
              <NuevaEmpresaDialog
                etiqueta="Registrar empresa"
                className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-border bg-card px-3 text-[12px] font-medium text-foreground/80 shadow-2xs transition-colors hover:bg-muted hover:text-foreground"
              />
            </div>
          </section>
        </div>

        <div className="grid min-w-0 grid-cols-1 gap-4 lg:col-span-5">
          <section className={cn(TARJETA, "p-5")}>
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
              <div className={TITULO_SECCION}>
                <FileKey2Icon className="size-4" />
                Certificado digital PKCS#12
              </div>
              <Punto tono={estadoCert.tono} />
            </div>
            <dl className="mt-4 grid grid-cols-1 gap-2 rounded-lg border border-border/80 bg-muted p-3.5 font-mono text-[12px]">
              <div className="flex items-center justify-between gap-3" title="Nombre del archivo: la API no lo conserva (solo el contenido cifrado)">
                <dt className="text-muted-foreground">Archivo cargado:</dt>
                <dd className={vigencia ? "text-foreground" : "text-muted-foreground/60"}>{vigencia ? "Sí (cifrado)" : "—"}</dd>
              </div>
              <div className="flex items-center justify-between gap-3" title="Huella SHA-256: próximamente (la API no la expone)">
                <dt className="text-muted-foreground">Huella SHA-256:</dt>
                <dd className="text-muted-foreground/60">—</dd>
              </div>
              <div className="flex items-center justify-between gap-3">
                <dt className="text-muted-foreground">Fecha expiración:</dt>
                <dd className={cn("font-medium", dias !== null && dias < 0 ? "text-destructive" : "text-foreground")}>{vigencia ?? "—"}</dd>
              </div>
              <div className="flex items-center justify-between gap-3" title="Entidad emisora: próximamente (la API no la expone)">
                <dt className="text-muted-foreground">Entidad emisora:</dt>
                <dd className="text-muted-foreground/60">—</dd>
              </div>
            </dl>
            <div className="mt-4">
              <CertificadoForm tieneCertificado={Boolean(vigencia)} />
            </div>
          </section>

          <section className={cn(TARJETA, "p-5")}>
            <div className={cn(TITULO_SECCION, "border-b border-border/60 pb-3")}>Recursos y normativa SUNAT</div>
            <ul className="mt-3 grid grid-cols-1 gap-2 text-[12px]">
              <li>
                <a
                  href="https://cpe.sunat.gob.pe/"
                  target="_blank"
                  rel="noreferrer"
                  className="flex items-center gap-2.5 rounded-lg border border-border/60 px-3 py-2 text-foreground/90 transition-colors hover:bg-muted"
                >
                  <HelpCircleIcon className="size-4 shrink-0 text-primary" />
                  <span className="min-w-0 flex-1">Portal de comprobantes de pago electrónicos (SUNAT)</span>
                  <ExternalLinkIcon className="size-3.5 shrink-0 text-muted-foreground" />
                </a>
              </li>
              <li>
                <a
                  href="https://www.gob.pe/indecopi"
                  target="_blank"
                  rel="noreferrer"
                  className="flex items-center gap-2.5 rounded-lg border border-border/60 px-3 py-2 text-foreground/90 transition-colors hover:bg-muted"
                >
                  <BadgeCheckIcon className="size-4 shrink-0 text-primary" />
                  <span className="min-w-0 flex-1">Entidades de certificación acreditadas por INDECOPI</span>
                  <ExternalLinkIcon className="size-3.5 shrink-0 text-muted-foreground" />
                </a>
              </li>
            </ul>
          </section>
        </div>
      </div>
    </div>
  );
}
