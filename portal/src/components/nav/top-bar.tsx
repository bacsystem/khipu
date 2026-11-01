"use client";

import { MobileNav } from "khipu-design-system/nav/mobile-nav";
import { TopBar as TopBarDelDiseno, type Miga, type PillEntorno } from "khipu-design-system/nav/top-bar";
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
import { SidebarContent } from "./sidebar-content";

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

/** Las acciones de la sección en la que se está: lo que cada página ofrece arriba a la derecha (la principal, y a lo sumo dos secundarias). */
function AccionesDeLaPagina({ usuario, apiBaseUrl }: { usuario: Usuario; apiBaseUrl: string }) {
  const pathname = usePathname();

  // Solo acciones que funcionan (C6): la búsqueda global, «Exportar», el monitoreo OSE y la prueba de conexión se veían pero no
  // hacían nada. Vuelven cuando existan.
  if (pathname.startsWith("/series"))
    return (
      <>
        <ReferenciaSeriesDialog className={cn(ACCION_SECUNDARIA, "hidden sm:inline-flex")} />
        <NuevaSerieDialog className={ACCION_PRINCIPAL} />
      </>
    );
  if (pathname.startsWith("/api-keys"))
    return (
      <>
        <ReferenciaApiKeysDialog className={cn(ACCION_SECUNDARIA, "hidden sm:inline-flex")} />
        <EjemploIntegracionDialog baseUrl={apiBaseUrl} className={cn(ACCION_SECUNDARIA, "hidden md:inline-flex")} />
        <NuevaApiKeyDialog className={ACCION_PRINCIPAL} />
      </>
    );
  if (pathname.startsWith("/empresa"))
    return (
      <>
        <ReferenciaEmpresaDialog className={cn(ACCION_SECUNDARIA, "hidden sm:inline-flex")} />
        <NuevaEmpresaDialog className={ACCION_PRINCIPAL} />
      </>
    );
  if (pathname.startsWith("/establecimientos")) return <EstablecimientoDialog className={ACCION_PRINCIPAL} />;
  if (pathname.startsWith("/clientes")) return <ClienteDialog className={ACCION_PRINCIPAL} />;
  if (pathname.startsWith("/productos")) return <ProductoDialog className={ACCION_PRINCIPAL} />;
  if (pathname.startsWith("/comprobantes") || pathname.startsWith("/inicio")) return <NuevoComprobanteDialog className={ACCION_PRINCIPAL} />;
  /* #39: «Nueva guía» es la acción de la sección, como «Nuevo comprobante» en la suya; en el propio formulario no hace falta. */
  if (pathname.startsWith("/guias") && pathname !== "/guias/nueva") return <NuevaGuiaEnlace />;
  if (pathname.startsWith("/cuenta/equipo") && usuario.rol === "ADMIN") return <InvitarDialog className={ACCION_PRINCIPAL} />;
  /* #277: Plan y consumo, Accesos de soporte y Developers no tienen una acción principal; emitir desde ahí no venía al caso. */
  return null;
}

/**
 * La barra superior del design system con lo propio del portal: la miga traducida (es el h1 de la página), el entorno de la empresa activa, el menú del teléfono (el mismo
 * menú lateral en un cajón) y las acciones de la sección.
 */
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
  const t = useMessages().nav;
  const migas: Miga[] = MIGAS.map((m) => ({ prefijo: m.prefijo, seccion: t.migas.secciones[m.seccion], pagina: m.pagina(t) }));
  const beta = entorno === "BETA";
  const pill: PillEntorno = beta
    ? { texto: t.entorno.betaSunat, tono: "aviso", title: t.entorno.betaAyuda }
    : { texto: t.entorno.produccion, tono: "oscuro", title: t.entorno.produccionAyuda };

  return (
    <TopBarDelDiseno
      migas={migas}
      pills={[pill]}
      menuMovil={
        <MobileNav>
          {/* El cajón del design system no se desplaza (su contenido es `h-full overflow-hidden`): en una pantalla baja el menú de usuario —tema, «Apariencia…», salir— quedaba
              fuera y no había cómo llegar a él. Se le da un contenedor que se desplaza y se deja crecer al contenido. */}
          <div className="h-full overflow-y-auto [&>div]:h-auto [&>div]:min-h-full [&>div]:overflow-visible">
            <SidebarContent usuario={usuario} cuenta={cuenta} empresas={empresas} activaId={activaId} />
          </div>
        </MobileNav>
      }
      acciones={<AccionesDeLaPagina usuario={usuario} apiBaseUrl={apiBaseUrl} />}
    />
  );
}
