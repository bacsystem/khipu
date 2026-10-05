"use client";

import { useState } from "react";
import { EditorDePlantilla } from "@/components/admin/editor-de-plantilla";
import { ListaDePlantillas, SeccionesDeConfiguracion } from "@/components/admin/configuracion-navegacion";
import { FormularioDeAviso } from "@/components/admin/formulario-de-aviso";
import { FormularioDeRemitente } from "@/components/admin/formulario-de-remitente";
import type { BannerConfigurado, PlantillaDeCorreo, RemitenteConfigurado } from "@/lib/api/admin-configuracion";

/** Lo que la página cargó de la sección que se ve (una sola: la sección va en la URL). */
export type ContenidoDeConfiguracion =
  | { seccion: "correo"; remitente: RemitenteConfigurado }
  | { seccion: "plantillas"; plantillas: PlantillaDeCorreo[]; elegida?: string }
  | { seccion: "aviso"; banner: BannerConfigurado | null };

/** El correo que se edita: el de la URL, o el primero si no existe. */
function correoElegido(plantillas: PlantillaDeCorreo[], elegida?: string): PlantillaDeCorreo | undefined {
  return plantillas.find((p) => p.tipo === elegida) ?? plantillas[0];
}

/** De qué es el mensaje de resultado: cada correo tiene el suyo, y las otras dos secciones también. */
function claveDe(c: ContenidoDeConfiguracion): string {
  return c.seccion === "plantillas" ? `plantillas:${correoElegido(c.plantillas, c.elegida)?.tipo ?? ""}` : c.seccion;
}

const PREFIJO = { correo: "correo", plantillas: "plantilla", aviso: "aviso" } as const;

/**
 * La sección que se ve de la configuración (#199). Cada formulario lleva una `key` que cambia cuando el servidor trae datos nuevos (guardar, restablecer, restaurar): así empieza
 * de nuevo desde lo que quedó guardado en lugar de arrastrar lo escrito antes. Por eso el «quedó guardado» **no** vive en el formulario: guardar recarga la página, la `key` cambia
 * y el formulario se vuelve a montar, y un mensaje que muriera con él se vería un instante y desaparecería. Vive acá, que no se vuelve a montar, y solo se muestra mientras se
 * sigue en lo mismo (la misma sección y, en «Plantillas», el mismo correo).
 */
export function ConfiguracionDeLaPlataforma({ contenido, ahora }: { contenido: ContenidoDeConfiguracion; ahora: string }) {
  const [resultado, setResultado] = useState<{ clave: string; mensaje: string } | null>(null);
  const clave = claveDe(contenido);
  const alResultado = (mensaje: string | null) => setResultado(mensaje === null ? null : { clave, mensaje });

  return (
    <div className="grid min-w-0 grid-cols-1 gap-4">
      <SeccionesDeConfiguracion actual={contenido.seccion} />
      {contenido.seccion === "correo" ? (
        <FormularioDeRemitente key={`${contenido.remitente.actualizado_en ?? "servidor"}|${contenido.remitente.vigente.email}`} remitente={contenido.remitente} alResultado={alResultado} />
      ) : null}
      {contenido.seccion === "plantillas" ? <Plantillas plantillas={contenido.plantillas} elegida={contenido.elegida} alResultado={alResultado} /> : null}
      {contenido.seccion === "aviso" ? (
        <FormularioDeAviso key={contenido.banner?.actualizado_en ?? "ninguno"} banner={contenido.banner} ahora={ahora} alResultado={alResultado} />
      ) : null}
      {resultado && resultado.clave === clave ? (
        <p data-testid={`${PREFIJO[contenido.seccion]}-resultado`} role="status" className="text-sm text-foreground">
          {resultado.mensaje}
        </p>
      ) : null}
    </div>
  );
}

function Plantillas({ plantillas, elegida, alResultado }: { plantillas: PlantillaDeCorreo[]; elegida?: string; alResultado: (mensaje: string | null) => void }) {
  const actual = correoElegido(plantillas, elegida);
  if (!actual) return null;
  return (
    <div className="grid min-w-0 gap-4 lg:grid-cols-[16rem_minmax(0,1fr)]">
      <ListaDePlantillas plantillas={plantillas} actual={actual.tipo} />
      <EditorDePlantilla key={`${actual.tipo}|${actual.actualizada_en ?? ""}`} plantilla={actual} alResultado={alResultado} />
    </div>
  );
}
