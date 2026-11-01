"use client";

import { SelectorContexto } from "khipu-design-system/nav/selector-contexto";
import { useMessages } from "next-intl";
import { seleccionarEmpresa } from "@/app/actions";
import type { Empresa } from "@/lib/api/empresas";
import { interpolar } from "@/i18n/mensajes";

/** La empresa activa de la cuenta: el selector de contexto del design system, con el RUC como detalle y la server action que fija la cookie al cambiar. */
export function EmpresaSelector({ empresas, activaId }: { empresas: Empresa[]; activaId?: string }) {
  const t = useMessages().nav;

  async function cambiar(id: string) {
    const formData = new FormData();
    formData.set("empresaId", id);
    await seleccionarEmpresa(formData);
  }

  return (
    <SelectorContexto
      items={empresas.map((e) => ({ id: e.id, nombre: e.razon_social, detalle: interpolar(t.empresas.ruc, { ruc: e.ruc }) }))}
      activoId={activaId}
      etiqueta={t.empresas.cambiar}
      onCambio={cambiar}
    />
  );
}
