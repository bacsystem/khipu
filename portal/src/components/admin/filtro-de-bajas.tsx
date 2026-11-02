"use client";

import { useMessages } from "next-intl";
import { SelectDeFiltro } from "@/components/compartidos/select-de-filtro";
import { bajasDesdeUrl, VISIBILIDADES_DE_BAJAS, type VisibilidadDeBajasAdmin } from "@/lib/api/admin-baja";

/**
 * Qué hacer con las cuentas dadas de baja (#201) en un listado: ocultarlas (lo que pasa sin pedir nada), mezclarlas o verlas solas. Es el mismo
 * selector en el listado de cuentas y en el de empresas, así los dos dicen lo mismo. «Ocultar» no es un valor de la URL: es no tener filtro.
 * Es una píldora de la barra de filtros, sin rótulo a la vista: cada opción dice por sí sola qué muestra.
 */
export function FiltroDeBajas({ valor, onCambio }: { valor?: VisibilidadDeBajasAdmin; onCambio: (valor: VisibilidadDeBajasAdmin | undefined) => void }) {
  const t = useMessages().admin.bajas;
  const TEXTO: Record<VisibilidadDeBajasAdmin, string> = { INCLUIDAS: t.incluidas, SOLO: t.solo };
  return (
    <SelectDeFiltro
      id="filtro-bajas"
      etiqueta={t.etiqueta}
      opciones={[{ value: "", label: t.ocultas }, ...VISIBILIDADES_DE_BAJAS.map((v) => ({ value: v, label: TEXTO[v] }))]}
      valor={valor ?? ""}
      onCambio={(v) => onCambio(bajasDesdeUrl(v))}
    />
  );
}
