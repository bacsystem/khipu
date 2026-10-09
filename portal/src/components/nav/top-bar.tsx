"use client";

import { usePathname } from "next/navigation";
import type { Usuario } from "@/lib/api/auth";
import type { MiCuenta } from "@/lib/api/cuenta";
import type { Empresa, Entorno } from "@/lib/api/empresas";
import { EjemploIntegracionDialog } from "@/components/api-keys/ejemplo-integracion";
import { NuevaApiKeyDialog } from "@/components/api-keys/nueva-api-key-dialog";
import { ReferenciaApiKeysDialog } from "@/components/api-keys/referencia-api-keys";
import { NuevaEmpresaDialog } from "@/components/empresa/nueva-empresa-dialog";
import { ReferenciaEmpresaDialog } from "@/components/empresa/referencia-empresa";
import { NuevoComprobanteDialog } from "@/components/comprobantes/nuevo-comprobante-dialog";
import { EstablecimientoDialog } from "@/components/establecimientos/establecimiento-dialog";
import { NuevaSerieDialog } from "@/components/series/nueva-serie-dialog";
import { ReferenciaSeriesDialog } from "@/components/series/referencia-series";
import { ACCION_PRINCIPAL, ACCION_SECUNDARIA } from "@/lib/estilos";
import { cn } from "@/lib/utils";
import { MobileNav } from "./mobile-nav";

const MIGAS: Array<{ prefijo: string; seccion: string; pagina: string }> = [
  { prefijo: "/comprobantes", seccion: "Facturación", pagina: "Comprobantes" },
  { prefijo: "/series", seccion: "Emisión", pagina: "Series correlativas" },
  {
    prefijo: "/empresa",
    seccion: "Configuración",
    pagina: "Fiscal & certificado",
  },
  { prefijo: "/establecimientos", seccion: "Configuración", pagina: "Establecimientos" },
  { prefijo: "/api-keys", seccion: "Configuración", pagina: "API keys & integración" },
  { prefijo: "/developers", seccion: "Configuración", pagina: "Developers" },
  { prefijo: "/cuenta/accesos-de-soporte", seccion: "Cuenta", pagina: "Accesos de soporte" },
  { prefijo: "/cuenta/plan", seccion: "Cuenta", pagina: "Plan y consumo" },
];

export function TopBar({
  entorno,
  usuario,
  cuenta,
  empresas,
  activaId,
  apiBaseUrl,
}: {
  entorno: Entorno;
  usuario: Usuario;
  cuenta: MiCuenta | null;
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
          <MobileNav usuario={usuario} cuenta={cuenta} empresas={empresas} activaId={activaId} />
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

      {/* Solo acciones que funcionan (C6): la búsqueda global, «Exportar», el monitoreo OSE y la prueba de conexión se veían pero no
          hacían nada. Vuelven cuando existan. */}
      <div className="flex shrink-0 items-center gap-2.5">
        {pathname.startsWith("/series") ? (
          <>
            <ReferenciaSeriesDialog className={cn(ACCION_SECUNDARIA, "hidden sm:inline-flex")} />
            <NuevaSerieDialog className={ACCION_PRINCIPAL} />
          </>
        ) : pathname.startsWith("/api-keys") ? (
          <>
            <ReferenciaApiKeysDialog className={cn(ACCION_SECUNDARIA, "hidden sm:inline-flex")} />
            <EjemploIntegracionDialog baseUrl={apiBaseUrl} className={cn(ACCION_SECUNDARIA, "hidden md:inline-flex")} />
            <NuevaApiKeyDialog className={ACCION_PRINCIPAL} />
          </>
        ) : pathname.startsWith("/empresa") ? (
          <>
            <ReferenciaEmpresaDialog className={cn(ACCION_SECUNDARIA, "hidden sm:inline-flex")} />
            <NuevaEmpresaDialog className={ACCION_PRINCIPAL} />
          </>
        ) : pathname.startsWith("/establecimientos") ? (
          <EstablecimientoDialog className={ACCION_PRINCIPAL} />
        ) : pathname.startsWith("/comprobantes") ? (
          <NuevoComprobanteDialog className={ACCION_PRINCIPAL} />
        ) : /* #277: Plan y consumo, Accesos de soporte y Developers no tienen una acción principal; emitir desde ahí no venía al caso. */ null}
      </div>
    </header>
  );
}
