"use client";

import { DownloadIcon, PlusIcon } from "lucide-react";
import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import type { Administrador } from "@/lib/api/admin-auth";
import { hrefExportacionConsumo, paramsConsumoDesdeUrl } from "@/lib/api/admin-consumo";
import { migaAdmin } from "@/lib/admin-migas";
import { ACCION_PRINCIPAL } from "@/lib/estilos";
import { hoyLima } from "@/lib/formato";
import { messages } from "@/lib/messages";
import { AdminMobileNav } from "./admin-mobile-nav";
import { FormularioDePlan } from "./formulario-de-plan";

/**
 * El CSV del consumo baja lo que se ve: el mes, el filtro y el orden de la URL de la página, sin la paginación. Sin mes en la URL la página mide el mes en
 * curso de Lima, así que se exporta ese (y no el que el backend tome al recibir la descarga, que podría ser otro si se cruza el fin de mes).
 */
function hrefExportacionDeLaPagina(busqueda: URLSearchParams): string {
  const params = paramsConsumoDesdeUrl(Object.fromEntries(busqueda.entries()));
  return hrefExportacionConsumo({ ...params, mes: params.mes ?? hoyLima().slice(0, 7) });
}

/**
 * Cabecera del backoffice, con el mismo esqueleto que la del portal de clientes: menú en móvil, miga de ubicación y la acción
 * principal de la página. La miga NO es un encabezado: cada página del backoffice ya trae su propio `h1`.
 */
export function AdminTopBar({ administrador }: { administrador: Administrador }) {
  const pathname = usePathname();
  const busqueda = useSearchParams();
  const miga = migaAdmin(pathname);
  const t = messages.admin.topbar;

  return (
    <header className="sticky top-0 z-20 flex h-14 shrink-0 items-center justify-between gap-3 border-b border-border/80 bg-card/80 px-4 backdrop-blur md:px-6">
      <div className="flex min-w-0 items-center gap-3">
        <div className="md:hidden">
          <AdminMobileNav administrador={administrador} />
        </div>

        {miga ? (
          <nav aria-label={t.ubicacion} className="flex min-w-0 shrink items-center gap-1.5 overflow-hidden text-[13px] whitespace-nowrap">
            <span className="text-muted-foreground/80">{miga.seccion}</span>
            <span aria-hidden="true" className="text-muted-foreground/40">
              /
            </span>
            <span aria-current="page" className="truncate font-heading font-semibold text-foreground">
              {miga.pagina}
            </span>
          </nav>
        ) : null}
      </div>

      <div className="flex shrink-0 items-center gap-2.5">
        {miga?.accion === "nuevaCuenta" ? (
          <Link href="/admin/cuentas/nueva" className={ACCION_PRINCIPAL}>
            <PlusIcon className="size-4" />
            {t.nuevaCuenta}
          </Link>
        ) : null}
        {/* Crear un plan es un modal, no una página: la cabecera ofrece el mismo formulario que antes vivía sobre la tabla. */}
        {miga?.accion === "nuevoPlan" ? <FormularioDePlan claseDelBoton={ACCION_PRINCIPAL} /> : null}
        {miga?.accion === "exportarConsumo" ? (
          <a href={hrefExportacionDeLaPagina(busqueda)} download className={ACCION_PRINCIPAL}>
            <DownloadIcon className="size-4" />
            {messages.admin.consumo.exportar}
          </a>
        ) : null}
      </div>
    </header>
  );
}
