"use client";

import { ChipSidebar, SidebarContent as SidebarDelDiseno } from "khipu-design-system/nav/sidebar";
import { useMessages } from "next-intl";
import type { Usuario } from "@/lib/api/auth";
import type { MiCuenta } from "@/lib/api/cuenta";
import type { Empresa } from "@/lib/api/empresas";
import { EmpresaSelector } from "./empresa-selector";
import { gruposDeNavegacion } from "./grupos-de-navegacion";
import { LogoMarca } from "./logo";
import { PerfilUsuario } from "./perfil-usuario";
import { PlanYConsumo } from "./plan-y-consumo";

/**
 * El contenido del menú lateral (en escritorio, la tarjeta flotante; en el teléfono, el cajón): el `SidebarContent` del design system con lo propio del portal en sus
 * espacios. Es un componente de cliente porque los íconos de los grupos son funciones y no pueden cruzar desde el layout del servidor.
 */
export function SidebarContent({
  usuario,
  cuenta,
  empresas,
  activaId,
}: {
  usuario: Usuario;
  /** La cuenta con su plan y su consumo (C1); `null` si no se pudo leer: el panel carga igual. */
  cuenta: MiCuenta | null;
  empresas: Empresa[];
  activaId?: string;
}) {
  const t = useMessages().nav;
  const activa = empresas.find((e) => e.id === activaId) ?? empresas[0];
  return (
    <SidebarDelDiseno
      marca={<LogoMarca />}
      // El entorno de la empresa activa, siempre a la vista: la «píldora» de la barra superior del design system se oculta en pantallas angostas, y emitir en producción
      // creyendo que es BETA (o al revés) es lo que no puede pasar desapercibido en el teléfono.
      chip={activa ? <ChipSidebar>{activa.entorno === "BETA" ? t.entorno.beta : t.entorno.produccion}</ChipSidebar> : undefined}
      selector={empresas.length > 0 ? <EmpresaSelector empresas={empresas} activaId={activaId} /> : undefined}
      grupos={gruposDeNavegacion(t, usuario.rol)}
      pie={<PlanYConsumo cuenta={cuenta} />}
      // El menú de usuario incluye el tema (en móvil la barra superior no lo muestra), «Apariencia…», el cambio de contraseña y el cierre de sesión.
      usuario={<PerfilUsuario usuario={usuario} nombreCuenta={cuenta?.nombre ?? null} />}
    />
  );
}
