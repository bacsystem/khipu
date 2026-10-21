import {
  BellIcon,
  BuildingIcon,
  CircleAlertIcon,
  CreditCardIcon,
  GaugeIcon,
  RadioIcon,
  SettingsIcon,
  ShieldCheckIcon,
  UsersIcon,
  type LucideIcon,
} from "lucide-react";
import type { Mensajes } from "@/i18n/mensajes";
import es from "@/messages/es.json";

export type ItemAdmin = { href: string; etiqueta: string; icono: LucideIcon; resumen: string };
export type SeccionAdmin = { titulo: string; items: ItemAdmin[] };

/**
 * Las páginas del backoffice agrupadas por sección. Es la única fuente del menú lateral, de la miga de la cabecera y de las tarjetas del inicio, para que
 * los tres digan lo mismo: antes el menú era una lista plana con un ítem «Operación» deshabilitado que no llevaba a ningún lado, mientras la miga ya
 * agrupaba Monitor, Errores, Avisos e Integridad bajo «Operación». Los textos son los de `messages` (los de la sesión, en un componente).
 */
export function seccionesAdmin(messages: Mensajes = es): SeccionAdmin[] {
  const t = messages.admin;
  const r = t.inicio.resumen;
  return [
    {
      titulo: t.topbar.clientes,
      items: [
        { href: "/admin/cuentas", etiqueta: t.nav.cuentas, icono: UsersIcon, resumen: r.cuentas },
        { href: "/admin/empresas", etiqueta: t.nav.empresas, icono: BuildingIcon, resumen: r.empresas },
      ],
    },
    {
      titulo: t.topbar.comercial,
      items: [
        { href: "/admin/planes", etiqueta: t.nav.planes, icono: CreditCardIcon, resumen: r.planes },
        { href: "/admin/consumo", etiqueta: t.nav.consumo, icono: GaugeIcon, resumen: r.consumo },
      ],
    },
    {
      titulo: t.topbar.operacion,
      items: [
        { href: "/admin/monitor", etiqueta: t.nav.monitor, icono: RadioIcon, resumen: r.monitor },
        { href: "/admin/errores", etiqueta: t.nav.errores, icono: CircleAlertIcon, resumen: r.errores },
        { href: "/admin/avisos", etiqueta: t.nav.avisos, icono: BellIcon, resumen: r.avisos },
        { href: "/admin/integridad", etiqueta: t.nav.integridad, icono: ShieldCheckIcon, resumen: r.integridad },
      ],
    },
    {
      titulo: t.topbar.plataforma,
      items: [{ href: "/admin/configuracion", etiqueta: t.nav.configuracion, icono: SettingsIcon, resumen: r.configuracion }],
    },
  ];
}

/** Las secciones en castellano. En un componente, `seccionesAdmin(messages)` con los textos de la sesión. */
export const SECCIONES_ADMIN: SeccionAdmin[] = seccionesAdmin();
