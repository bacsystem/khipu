import { TruckIcon } from "@phosphor-icons/react/dist/ssr";
import { EstadoVacio } from "khipu-design-system/feedback/estado-vacio";
import { EnlaceVolver } from "khipu-design-system/patrones/cabecera-pagina";
import Link from "next/link";
import type { Metadata } from "next";
import { getMessages } from "next-intl/server";
import { AvisoExperimental } from "@/components/guias/aviso-experimental";
import { NuevaGuiaForm, type OpcionDeCatalogo } from "@/components/guias/nueva-guia-form";
import { obtenerCatalogo } from "@/lib/api/catalogos";
import { listarEstablecimientos } from "@/lib/api/establecimientos";
import { listarSeries } from "@/lib/api/series";
import { hoyLima } from "@/lib/formato";
import { ACCION_PRINCIPAL, CONTENEDOR_PAGINA } from "@/lib/estilos";
import { getServerSession } from "@/lib/session-server";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.guias.form.titulo} · ${messages.app.nombre}` };
}

/** Las entradas de un catálogo; si no se puede leer, vacío, y el formulario deja escribir el código a mano en vez de impedir emitir. */
async function entradas(id: string): Promise<OpcionDeCatalogo[]> {
  try {
    return (await obtenerCatalogo(id)).entradas.map((e) => ({ codigo: e.codigo, descripcion: e.descripcion, extra: e.extra }));
  } catch {
    return [];
  }
}

export default async function NuevaGuiaPage() {
  const { access, empresaId } = await getServerSession();
  const messages = await getMessages();
  const t = messages.guias;

  if (!access || !empresaId) {
    return (
      <p className="text-cuerpo text-muted-foreground">
        {t.pagina.sinEmpresa}{" "}
        <Link href="/onboarding" className="text-primary hover:underline">
          {t.pagina.configurarEmpresa}
        </Link>
      </p>
    );
  }

  const [series, establecimientos, motivos, tiposDocumento, documentos] = await Promise.all([
    listarSeries(access, empresaId),
    // Los establecimientos son una ayuda para elegir el punto de partida o llegada: si no se pueden leer, queda el domicilio fiscal y las direcciones a mano.
    listarEstablecimientos(access, empresaId).catch(() => []),
    entradas("20"),
    entradas("06"),
    entradas("61"),
  ]);
  const seriesDeGuia = series.filter((s) => s.activa && (s.tipo === "09" || s.tipo === "31"));

  return (
    <div className={CONTENEDOR_PAGINA}>
      <div>
        <EnlaceVolver href="/guias">{t.form.volver}</EnlaceVolver>
      </div>
      <AvisoExperimental />
      {seriesDeGuia.length === 0 ? (
        <div data-testid="guias-sin-serie">
          <EstadoVacio
            icon={TruckIcon}
            titulo={t.pagina.sinSerieTitulo}
            accion={
              <Link href="/series" className={ACCION_PRINCIPAL}>
                {t.pagina.crearSerie}
              </Link>
            }
          >
            {t.pagina.sinSerie}
          </EstadoVacio>
        </div>
      ) : (
        <NuevaGuiaForm series={seriesDeGuia} establecimientos={establecimientos} catalogos={{ motivos, tiposDocumento, documentos }} hoy={hoyLima()} />
      )}
    </div>
  );
}
