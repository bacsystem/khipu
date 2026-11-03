import type { Metadata } from "next";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { getMessages } from "next-intl/server";
import { CabeceraPagina } from "khipu-design-system/patrones/cabecera-pagina";
import Link from "next/link";
import { redirect } from "next/navigation";
import { ConfiguracionDeLaPlataforma, type ContenidoDeConfiguracion } from "@/components/admin/configuracion-de-la-plataforma";
import { getAdminServerSession } from "@/lib/admin-session-server";
import {
  hrefConfiguracion,
  listarPlantillas,
  obtenerBanner,
  obtenerRemitente,
  paramsConfiguracionDesdeUrl,
  type ParamsConfiguracion,
} from "@/lib/api/admin-configuracion";
import { CONTENEDOR_PAGINA } from "@/lib/estilos";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: messages.admin.metadatos.configuracion };
}

/** Solo se pide lo que se ve: la sección va en la URL. */
async function cargar(access: string, p: ParamsConfiguracion): Promise<ContenidoDeConfiguracion> {
  switch (p.seccion) {
    case "correo":
      return { seccion: "correo", remitente: await obtenerRemitente(access) };
    case "plantillas":
      return { seccion: "plantillas", plantillas: await listarPlantillas(access), elegida: p.plantilla };
    case "aviso":
      return { seccion: "aviso", banner: await obtenerBanner(access) };
  }
}

/**
 * Configuración de la plataforma (#199): el remitente de los correos, el texto de cada correo y el aviso de mantenimiento, sin esperar un despliegue. No captura el 401 por
 * separado: el layout del panel valida la sesión del administrador (`/me`) en cada render, así que un token vencido ya redirigió a /admin/login antes de llegar aquí.
 */
export default async function AdminConfiguracionPage({ searchParams }: { searchParams: Promise<{ seccion?: string; plantilla?: string }> }) {
  const messages = await getMessages();
  const t = messages.admin.configuracion;
  const { access } = await getAdminServerSession();
  if (!access) redirect("/admin/login");

  const params = paramsConfiguracionDesdeUrl(await searchParams);
  const ahora = new Date().toISOString();
  const cargado = await cargar(access, params).then(
    (contenido) => ({ contenido }),
    () => ({ contenido: null }),
  );

  return (
    <div className={CONTENEDOR_PAGINA}>
      <CabeceraPagina titulo={t.titulo} descripcion={t.descripcion} />

      {cargado.contenido ? (
        <ConfiguracionDeLaPlataforma contenido={cargado.contenido} ahora={ahora} />
      ) : (
        <Alerta
          tono="error"
          accion={
            <Link href={hrefConfiguracion(params)} className="font-medium underline">
              {t.volverACargar}
            </Link>
          }
        >
          {t.error}
        </Alerta>
      )}
    </div>
  );
}
