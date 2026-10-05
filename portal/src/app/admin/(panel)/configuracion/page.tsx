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
import { messages } from "@/lib/messages";

export const metadata = { title: "Configuración · Backoffice" };

const t = messages.admin.configuracion;

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
  const { access } = await getAdminServerSession();
  if (!access) redirect("/admin/login");

  const params = paramsConfiguracionDesdeUrl(await searchParams);
  const ahora = new Date().toISOString();
  const cargado = await cargar(access, params).then(
    (contenido) => ({ contenido }),
    () => ({ contenido: null }),
  );

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <div className="grid gap-1">
        <h1 className="font-heading text-2xl">{t.titulo}</h1>
        <p className="max-w-4xl text-sm text-muted-foreground">{t.descripcion}</p>
      </div>

      {cargado.contenido ? (
        <ConfiguracionDeLaPlataforma contenido={cargado.contenido} ahora={ahora} />
      ) : (
        <div role="alert" className="flex flex-wrap items-center gap-3 rounded-xl border border-destructive-border bg-destructive/10 px-4 py-3 text-sm text-destructive">
          <span>{t.error}</span>
          <Link href={hrefConfiguracion(params)} className="font-medium underline">
            {t.volverACargar}
          </Link>
        </div>
      )}
    </div>
  );
}
