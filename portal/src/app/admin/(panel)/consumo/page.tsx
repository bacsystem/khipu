import type { Metadata } from "next";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { getMessages } from "next-intl/server";
import { CabeceraPagina } from "khipu-design-system/patrones/cabecera-pagina";
import { redirect } from "next/navigation";
import Link from "next/link";
import { ConsumoTabla } from "@/components/admin/consumo-tabla";
import { getAdminServerSession } from "@/lib/admin-session-server";
import { hrefConsumo, hrefSiFueraDeRango, listarConsumoDeCuentas, paramsConsumoDesdeUrl } from "@/lib/api/admin-consumo";
import { CONTENEDOR_PAGINA } from "@/lib/estilos";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: messages.admin.metadatos.consumo };
}

/**
 * Consumo de todas las cuentas contra el tope de su plan (#193). La vista, el mes, el orden y la página viven en la URL y se aplican ya en el render del servidor.
 * No captura el 401 por separado: el layout del panel valida la sesión del administrador (`/me`) en cada render, así que un token vencido ya redirigió a
 * /admin/login antes de llegar aquí.
 */
export default async function AdminConsumoPage({ searchParams }: { searchParams: Promise<{ mes?: string; filtro?: string; orden?: string; pagina?: string; por_pagina?: string }> }) {
  const messages = await getMessages();
  const { access } = await getAdminServerSession();
  if (!access) redirect("/admin/login");

  const params = paramsConsumoDesdeUrl(await searchParams);
  const resultado = await listarConsumoDeCuentas(access, params).then(
    (pagina) => ({ pagina }),
    () => ({ pagina: null }),
  );
  // Fuera del `.then`/catch de arriba: `redirect` lanza, y no debe confundirse con un fallo del backend.
  const corregida = resultado.pagina ? hrefSiFueraDeRango(params, resultado.pagina.total) : null;
  if (corregida) redirect(corregida);

  return (
    <div className={CONTENEDOR_PAGINA}>
      <CabeceraPagina titulo={messages.admin.consumo.titulo} descripcion={messages.admin.consumo.descripcion} />

      {resultado.pagina ? (
        <ConsumoTabla datos={resultado.pagina.datos} total={resultado.pagina.total} params={params} />
      ) : (
        <Alerta
          tono="error"
          accion={
            <Link href={hrefConsumo(params)} className="font-medium underline">
              {messages.admin.consumo.reintentar}
            </Link>
          }
        >
          {messages.admin.consumo.error}
        </Alerta>
      )}
    </div>
  );
}
