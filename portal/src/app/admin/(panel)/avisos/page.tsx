import type { Metadata } from "next";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { getMessages } from "next-intl/server";
import { CabeceraPagina } from "khipu-design-system/patrones/cabecera-pagina";
import Link from "next/link";
import { redirect } from "next/navigation";
import { AvisosTabla } from "@/components/admin/avisos-tabla";
import { getAdminServerSession } from "@/lib/admin-session-server";
import { hrefEmpresas } from "@/lib/api/admin-empresas";
import { hrefAvisos, hrefSiFueraDeRango, listarCertificadosEnRiesgo, listarCredencialesSolFallando, paramsAvisosDesdeUrl } from "@/lib/api/admin-avisos";
import { POR_PAGINA_DEFECTO } from "khipu-design-system/lib/paginacion";
import { CONTENEDOR_PAGINA } from "@/lib/estilos";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: messages.admin.metadatos.avisos };
}

/**
 * Avisos a clientes (#197): certificados por vencer o vencidos y credenciales SOL que SUNAT no acepta. La lista, la página y el tamaño viven en la URL y se aplican ya en el render
 * del servidor; solo se pide la lista que se ve. No captura el 401 por separado: el layout del panel valida la sesión del administrador (`/me`) en cada render, así que un token
 * vencido ya redirigió a /admin/login antes de llegar aquí.
 */
export default async function AdminAvisosPage({ searchParams }: { searchParams: Promise<{ vista?: string; pagina?: string; por_pagina?: string }> }) {
  const messages = await getMessages();
  const { access } = await getAdminServerSession();
  if (!access) redirect("/admin/login");

  const params = paramsAvisosDesdeUrl(await searchParams);
  const resultado = await (params.vista === "CERTIFICADOS"
    ? listarCertificadosEnRiesgo(access, params).then((certificados) => ({ certificados, sol: null, total: certificados.total }))
    : listarCredencialesSolFallando(access, params).then((sol) => ({ certificados: null, sol, total: sol.total }))
  ).then(
    (r) => ({ r }),
    () => ({ r: null }),
  );
  // Fuera del `.then`/catch de arriba: `redirect` lanza, y no debe confundirse con un fallo del backend.
  const corregida = resultado.r ? hrefSiFueraDeRango(params, resultado.r.total) : null;
  if (corregida) redirect(corregida);

  return (
    <div className={CONTENEDOR_PAGINA}>
      <CabeceraPagina
        titulo={messages.admin.avisos.titulo}
        descripcion={
          <>
            {messages.admin.avisos.descripcion}{" "}
            {/* H21: una empresa que nunca cargó certificado tampoco puede emitir, y no tiene nada que vencer: no sale en estas listas. */}
            {messages.admin.avisos.sinCertificado}{" "}
            <Link href={hrefEmpresas({ certificado: "SIN_CERTIFICADO", pagina: 1, porPagina: POR_PAGINA_DEFECTO })} className="font-medium text-primary hover:underline">
              {messages.admin.avisos.verSinCertificado}
            </Link>
          </>
        }
      />

      {resultado.r ? (
        <AvisosTabla params={params} certificados={resultado.r.certificados} sol={resultado.r.sol} />
      ) : (
        <Alerta
          tono="error"
          accion={
            <Link href={hrefAvisos(params)} className="font-medium underline">
              {messages.admin.avisos.volverACargar}
            </Link>
          }
        >
          {messages.admin.avisos.error}
        </Alerta>
      )}
    </div>
  );
}
