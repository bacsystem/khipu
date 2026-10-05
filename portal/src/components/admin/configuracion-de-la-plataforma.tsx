import { EditorDePlantilla } from "@/components/admin/editor-de-plantilla";
import { ListaDePlantillas, SeccionesDeConfiguracion } from "@/components/admin/configuracion-navegacion";
import { FormularioDeAviso } from "@/components/admin/formulario-de-aviso";
import { FormularioDeRemitente } from "@/components/admin/formulario-de-remitente";
import type { BannerConfigurado, PlantillaDeCorreo, RemitenteConfigurado, SeccionDeConfiguracion } from "@/lib/api/admin-configuracion";

/** Lo que la página cargó de la sección que se ve (una sola: la sección va en la URL). */
export type ContenidoDeConfiguracion =
  | { seccion: "correo"; remitente: RemitenteConfigurado }
  | { seccion: "plantillas"; plantillas: PlantillaDeCorreo[]; elegida?: string }
  | { seccion: "aviso"; banner: BannerConfigurado | null };

/**
 * La sección que se ve de la configuración (#199). Cada formulario lleva una `key` que cambia cuando el servidor trae datos nuevos (guardar, restablecer, restaurar): así empieza
 * de nuevo desde lo que quedó guardado en lugar de arrastrar lo escrito antes. El correo elegido en «Plantillas» es el de la URL, o el primero si no existe.
 */
export function ConfiguracionDeLaPlataforma({ contenido, ahora }: { contenido: ContenidoDeConfiguracion; ahora: string }) {
  const seccion: SeccionDeConfiguracion = contenido.seccion;
  return (
    <div className="grid min-w-0 grid-cols-1 gap-4">
      <SeccionesDeConfiguracion actual={seccion} />
      {contenido.seccion === "correo" ? (
        <FormularioDeRemitente key={`${contenido.remitente.actualizado_en ?? "servidor"}|${contenido.remitente.vigente.email}`} remitente={contenido.remitente} />
      ) : null}
      {contenido.seccion === "plantillas" ? <Plantillas plantillas={contenido.plantillas} elegida={contenido.elegida} /> : null}
      {contenido.seccion === "aviso" ? <FormularioDeAviso key={contenido.banner?.actualizado_en ?? "ninguno"} banner={contenido.banner} ahora={ahora} /> : null}
    </div>
  );
}

function Plantillas({ plantillas, elegida }: { plantillas: PlantillaDeCorreo[]; elegida?: string }) {
  const actual = plantillas.find((p) => p.tipo === elegida) ?? plantillas[0];
  if (!actual) return null;
  return (
    <div className="grid min-w-0 gap-4 lg:grid-cols-[16rem_minmax(0,1fr)]">
      <ListaDePlantillas plantillas={plantillas} actual={actual.tipo} />
      <EditorDePlantilla key={`${actual.tipo}|${actual.actualizada_en ?? ""}`} plantilla={actual} />
    </div>
  );
}
