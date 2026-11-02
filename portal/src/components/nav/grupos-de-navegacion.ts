import { EyeIcon, GaugeIcon, ListNumbersIcon, PackageIcon, ReceiptIcon, ShieldCheckIcon, SquaresFourIcon, StorefrontIcon, TerminalWindowIcon, TruckIcon, UserGearIcon, UsersIcon } from "@phosphor-icons/react";
import type { GrupoNav } from "khipu-design-system/nav/sidebar-nav";
import type { Mensajes } from "@/i18n/mensajes";

type Item = {
  href: string;
  /** Clave en `nav.items` de los mensajes. */
  label: keyof Mensajes["nav"]["items"];
  icon: GrupoNav["items"][number]["icon"];
  /** #21: solo para administradores. Los demás roles reciben 403 del backend al leerla, así que ni se muestra. */
  soloAdmin?: boolean;
};

type Grupo = {
  /** Clave en `nav.grupos` de los mensajes. */
  titulo: keyof Mensajes["nav"]["grupos"];
  items: Item[];
};

/** Solo páginas que existen (C6): «Catálogo» se veía con «Pronto» y no llevaba a ningún lado. */
const GRUPOS: Grupo[] = [
  {
    // #24: el panel de inicio, adonde se llega al entrar.
    titulo: "panel",
    items: [{ href: "/inicio", label: "inicio", icon: SquaresFourIcon }],
  },
  {
    titulo: "emision",
    items: [
      { href: "/comprobantes", label: "comprobantes", icon: ReceiptIcon },
      { href: "/guias", label: "guias", icon: TruckIcon },
      { href: "/series", label: "series", icon: ListNumbersIcon },
      { href: "/clientes", label: "clientes", icon: UsersIcon },
      { href: "/productos", label: "productos", icon: PackageIcon },
    ],
  },
  {
    titulo: "configuracion",
    items: [
      { href: "/empresa", label: "empresa", icon: ShieldCheckIcon },
      { href: "/establecimientos", label: "establecimientos", icon: StorefrontIcon },
      { href: "/api-keys", label: "apiKeys", icon: TerminalWindowIcon, soloAdmin: true },
    ],
  },
  {
    // Lo de la cuenta y no de una empresa, como en la miga («Cuenta / …»).
    titulo: "cuenta",
    items: [
      { href: "/cuenta/plan", label: "plan", icon: GaugeIcon },
      { href: "/cuenta/equipo", label: "equipo", icon: UserGearIcon, soloAdmin: true },
      { href: "/cuenta/accesos-de-soporte", label: "soporte", icon: EyeIcon },
    ],
  },
];

/**
 * Los grupos del menú lateral ya traducidos, en la forma que recibe el `SidebarNav` del design system. `rol` es el del usuario de la sesión (#21): lo que es solo
 * de administradores no se ofrece a los demás.
 */
export function gruposDeNavegacion(t: Mensajes["nav"], rol: string): GrupoNav[] {
  return GRUPOS.map((grupo) => ({
    titulo: t.grupos[grupo.titulo],
    items: grupo.items.filter((item) => !item.soloAdmin || rol === "ADMIN").map((item) => ({ href: item.href, label: t.items[item.label], icon: item.icon })),
  }));
}
