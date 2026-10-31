"use client";

import { usePathname } from "next/navigation";
import { useMessages } from "next-intl";
import type { Mensajes } from "@/i18n/mensajes";
import type { Usuario } from "@/lib/api/auth";
import type { MiCuenta } from "@/lib/api/cuenta";
import type { Empresa, Entorno } from "@/lib/api/empresas";
import { EjemploIntegracionDialog } from "@/components/api-keys/ejemplo-integracion";
import { NuevaApiKeyDialog } from "@/components/api-keys/nueva-api-key-dialog";
import { ReferenciaApiKeysDialog } from "@/components/api-keys/referencia-api-keys";
import { NuevaEmpresaDialog } from "@/components/empresa/nueva-empresa-dialog";
import { InvitarDialog } from "@/components/equipo/invitar-dialog";
import { ReferenciaEmpresaDialog } from "@/components/empresa/referencia-empresa";
import { NuevoComprobanteDialog } from "@/components/comprobantes/nuevo-comprobante-dialog";
import { NuevaGuiaEnlace } from "@/components/guias/nueva-guia-enlace";
import { ClienteDialog } from "@/components/clientes/cliente-dialog";
import { ProductoDialog } from "@/components/productos/producto-dialog";
import { EstablecimientoDialog } from "@/components/establecimientos/establecimiento-dialog";
import { NuevaSerieDialog } from "@/components/series/nueva-serie-dialog";
import { ReferenciaSeriesDialog } from "@/components/series/referencia-series";
import { ACCION_PRINCIPAL, ACCION_SECUNDARIA } from "@/lib/estilos";
import { cn } from "@/lib/utils";
import { MobileNav } from "./mobile-nav";

type Nav = Mensajes["nav"];

/** La miga de cada sección. `pagina` es un nombre del menú (`nav.items`) o una página propia de la miga (`nav.migas.paginas`). */
const MIGAS: Array<{ prefijo: string; seccion: keyof Nav["migas"]["secciones"]; pagina: (t: Nav) => string }> = [
  { prefijo: "/inicio", seccion: "panel", pagina: (t) => t.items.inicio },
  { prefijo: "/comprobantes", seccion: "facturacion", pagina: (t) => t.items.comprobantes },
  { prefijo: "/guias", seccion: "facturacion", pagina: (t) => t.items.guias },
  { prefijo: "/series", seccion: "emision", pagina: (t) => t.items.series },
  { prefijo: "/clientes", seccion: "emision", pagina: (t) => t.items.clientes },
  { prefijo: "/productos", seccion: "emision", pagina: (t) => t.items.productos },
  { prefijo: "/empresa", seccion: "configuracion", pagina: (t) => t.items.empresa },
  { prefijo: "/establecimientos", seccion: "configuracion", pagina: (t) => t.items.establecimientos },
  { prefijo: "/api-keys", seccion: "configuracion", pagina: (t) => t.migas.paginas.apiKeys },
  { prefijo: "/developers", seccion: "configuracion", pagina: (t) => t.migas.paginas.developers },
  { prefijo: "/cuenta/accesos-de-soporte", seccion: "cuenta", pagina: (t) => t.items.soporte },
  { prefijo: "/cuenta/plan", seccion: "cuenta", pagina: (t) => t.items.plan },
  { prefijo: "/cuenta/equipo", seccion: "cuenta", pagina: (t) => t.items.equipo },
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
  const t = useMessages().nav;
  const miga = MIGAS.find((m) => pathname.startsWith(m.prefijo));
  const beta = entorno === "BETA";

  return (
    <header className="sticky top-0 z-20 flex h-14 shrink-0 items-center justify-between gap-3 border-b border-border/80 bg-card/80 px-4 backdrop-blur md:px-6">
      <div className="flex min-w-0 items-center gap-3">
        <div className="md:hidden">
          <MobileNav usuario={usuario} cuenta={cuenta} empresas={empresas} activaId={activaId} />
        </div>

        {miga ? (
          <nav aria-label={t.ubicacion} className="flex min-w-0 shrink items-center gap-1.5 overflow-hidden text-[13px] whitespace-nowrap">
            <span className="text-muted-foreground/80">{t.migas.secciones[miga.seccion]}</span>
            <span className="text-muted-foreground/40">/</span>
            <h1 className="truncate font-heading font-semibold text-foreground">{miga.pagina(t)}</h1>
          </nav>
        ) : null}

        <div className="hidden h-4 w-px bg-border sm:block" />

        <span
          title={beta ? t.entorno.betaAyuda : t.entorno.produccionAyuda}
          className={cn(
            "inline-flex items-center gap-1 rounded px-2 py-0.5 text-[11px] font-medium whitespace-nowrap",
            beta ? "border border-warning-border bg-warning text-warning-foreground" : "bg-foreground text-background",
          )}
        >
          <span className={cn("size-1.5 rounded-full", beta ? "bg-warning-solid" : "bg-success-solid")} />
          {beta ? t.entorno.betaSunat : t.entorno.produccion}
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
        ) : pathname.startsWith("/clientes") ? (
          <ClienteDialog className={ACCION_PRINCIPAL} />
        ) : pathname.startsWith("/productos") ? (
          <ProductoDialog className={ACCION_PRINCIPAL} />
        ) : pathname.startsWith("/comprobantes") || pathname.startsWith("/inicio") ? (
          <NuevoComprobanteDialog className={ACCION_PRINCIPAL} />
        ) : pathname.startsWith("/guias") && pathname !== "/guias/nueva" ? (
          /* #39: «Nueva guía» es la acción de la sección, como «Nuevo comprobante» en la suya; en el propio formulario no hace falta. */
          <NuevaGuiaEnlace />
        ) : pathname.startsWith("/cuenta/equipo") && usuario.rol === "ADMIN" ? (
          <InvitarDialog className={ACCION_PRINCIPAL} />
        ) : /* #277: Plan y consumo, Accesos de soporte y Developers no tienen una acción principal; emitir desde ahí no venía al caso. */ null}
      </div>
    </header>
  );
}
