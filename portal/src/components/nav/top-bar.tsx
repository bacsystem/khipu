"use client";

import { DownloadIcon, PlusIcon, RefreshCwIcon, SearchIcon } from "lucide-react";
import { usePathname } from "next/navigation";
import type { Usuario } from "@/lib/api/auth";
import type { Empresa, Entorno } from "@/lib/api/empresas";
import { EjemploIntegracionDialog } from "@/components/api-keys/ejemplo-integracion";
import { NuevaApiKeyDialog } from "@/components/api-keys/nueva-api-key-dialog";
import { ReferenciaApiKeysDialog } from "@/components/api-keys/referencia-api-keys";
import { NuevaEmpresaDialog } from "@/components/empresa/nueva-empresa-dialog";
import { ReferenciaEmpresaDialog } from "@/components/empresa/referencia-empresa";
import { NuevaSerieDialog } from "@/components/series/nueva-serie-dialog";
import { ReferenciaSeriesDialog } from "@/components/series/referencia-series";
import { cn } from "@/lib/utils";
import { MobileNav } from "./mobile-nav";

const ACCION_PRINCIPAL = "flex h-8 items-center gap-1.5 rounded-lg bg-foreground px-3 text-[12px] font-medium whitespace-nowrap text-background shadow-xs";
const ACCION_SECUNDARIA =
  "h-8 items-center gap-1.5 rounded-lg border border-border bg-card px-2.5 text-[12px] font-medium whitespace-nowrap text-foreground/80 shadow-2xs transition-colors hover:bg-muted hover:text-foreground";

const MIGAS: Array<{ prefijo: string; seccion: string; pagina: string }> = [
  { prefijo: "/comprobantes", seccion: "Facturación", pagina: "Comprobantes" },
  { prefijo: "/series", seccion: "Emisión", pagina: "Series correlativas" },
  {
    prefijo: "/empresa",
    seccion: "Configuración",
    pagina: "Fiscal & certificado",
  },
  { prefijo: "/api-keys", seccion: "Configuración", pagina: "API keys & integración" },
  { prefijo: "/developers", seccion: "Configuración", pagina: "Developers" },
];

export function TopBar({
  entorno,
  usuario,
  empresas,
  activaId,
  apiBaseUrl,
}: {
  entorno: Entorno;
  usuario: Usuario;
  empresas: Empresa[];
  activaId?: string;
  /** URL pública de la API, para los ejemplos de integración. */
  apiBaseUrl: string;
}) {
  const pathname = usePathname();
  const miga = MIGAS.find((m) => pathname.startsWith(m.prefijo));
  const beta = entorno === "BETA";

  return (
    <header className="sticky top-0 z-20 flex h-14 shrink-0 items-center justify-between gap-3 border-b border-border/80 bg-card/80 px-4 backdrop-blur md:px-6">
      <div className="flex min-w-0 items-center gap-3">
        <div className="md:hidden">
          <MobileNav usuario={usuario} empresas={empresas} activaId={activaId} />
        </div>

        {miga ? (
          <nav aria-label="Ubicación" className="flex min-w-0 shrink items-center gap-1.5 overflow-hidden text-[13px] whitespace-nowrap">
            <span className="text-muted-foreground/80">{miga.seccion}</span>
            <span className="text-muted-foreground/40">/</span>
            <h1 className="truncate font-heading font-semibold text-foreground">{miga.pagina}</h1>
          </nav>
        ) : null}

        <div className="hidden h-4 w-px bg-border sm:block" />

        <span
          title="Monitoreo de conexión con OSE/SUNAT: próximamente"
          className="hidden items-center gap-2 rounded-full border border-border/80 bg-muted px-2 py-0.5 text-[11px] whitespace-nowrap text-muted-foreground opacity-60 sm:inline-flex"
        >
          <span className="size-1.5 rounded-full bg-muted-foreground/40" />
          <span className="font-medium">OSE</span>
          <span className="text-muted-foreground/40">·</span>
          <span className="font-mono text-[10px]">sin monitoreo</span>
        </span>

        <span
          title={beta ? "Entorno BETA de SUNAT: los comprobantes emitidos aquí no tienen validez tributaria" : "Entorno de producción de SUNAT"}
          className={cn(
            "inline-flex items-center gap-1 rounded px-2 py-0.5 text-[11px] font-medium whitespace-nowrap",
            beta ? "border border-warning-border bg-warning text-warning-foreground" : "bg-foreground text-background",
          )}
        >
          <span className={cn("size-1.5 rounded-full", beta ? "bg-warning-solid" : "bg-success-solid")} />
          {beta ? "BETA Sunat" : "Producción"}
        </span>
      </div>

      <div className="flex shrink-0 items-center gap-2.5">
        <div className="relative hidden w-64 lg:block" title="Búsqueda por serie, RUC o cliente: próximamente">
          <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground/70" />
          <input
            disabled
            placeholder="Buscar por serie, RUC o cliente..."
            className="h-8 w-full rounded-lg border border-border bg-muted pr-12 pl-8 text-[12px] text-foreground placeholder:text-muted-foreground/70 disabled:cursor-not-allowed"
          />
          <kbd className="pointer-events-none absolute top-1/2 right-2 -translate-y-1/2 rounded border border-border bg-card px-1.5 py-0.5 font-mono text-[10px] text-muted-foreground/70 shadow-2xs">
            ⌘K
          </kbd>
        </div>
        <div className="hidden h-4 w-px bg-border lg:block" />
        <button
          disabled
          title="Exportar reporte: próximamente"
          className="hidden h-8 items-center gap-1.5 rounded-lg border border-border px-2.5 text-[12px] font-medium text-muted-foreground disabled:cursor-not-allowed sm:flex"
        >
          <DownloadIcon className="size-4" />
          Exportar
        </button>
        {pathname.startsWith("/series") ? (
          <>
            <ReferenciaSeriesDialog className={cn(ACCION_SECUNDARIA, "hidden sm:inline-flex")} />
            <NuevaSerieDialog className={cn(ACCION_PRINCIPAL, "transition-colors hover:bg-foreground/90")} />
          </>
        ) : pathname.startsWith("/api-keys") ? (
          <>
            <ReferenciaApiKeysDialog className={cn(ACCION_SECUNDARIA, "hidden sm:inline-flex")} />
            <EjemploIntegracionDialog baseUrl={apiBaseUrl} className={cn(ACCION_SECUNDARIA, "hidden md:inline-flex")} />
            <NuevaApiKeyDialog className={cn(ACCION_PRINCIPAL, "transition-colors hover:bg-foreground/90")} />
          </>
        ) : pathname.startsWith("/empresa") ? (
          <>
            <ReferenciaEmpresaDialog className={cn(ACCION_SECUNDARIA, "hidden sm:inline-flex")} />
            <button
              disabled
              title="Prueba de conexión con SUNAT: próximamente"
              className={cn(ACCION_SECUNDARIA, "hidden disabled:cursor-not-allowed disabled:opacity-60 md:inline-flex")}
            >
              <RefreshCwIcon className="size-4" />
              Probar conexión SUNAT
            </button>
            <NuevaEmpresaDialog className={cn(ACCION_PRINCIPAL, "transition-colors hover:bg-foreground/90")} />
          </>
        ) : (
          <button
            disabled
            title="Los comprobantes se emiten por integración con la API, no desde el portal todavía"
            className={cn(ACCION_PRINCIPAL, "disabled:cursor-not-allowed")}
          >
            <PlusIcon className="size-4" />
            Nueva factura
          </button>
        )}
      </div>
    </header>
  );
}
