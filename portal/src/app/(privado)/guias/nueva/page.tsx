import { ArrowLeftIcon } from "@phosphor-icons/react/dist/ssr";
import { PESO_ICONO } from "khipu-design-system/lib/iconos";
import Link from "next/link";
import type { Metadata } from "next";
import { getMessages } from "next-intl/server";
import { AvisoExperimental } from "@/components/guias/aviso-experimental";
import { NuevaGuiaForm, type OpcionDeCatalogo } from "@/components/guias/nueva-guia-form";
import { obtenerCatalogo } from "@/lib/api/catalogos";
import { listarEstablecimientos } from "@/lib/api/establecimientos";
import { listarSeries } from "@/lib/api/series";
import { hoyLima } from "@/lib/formato";
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
    <div className="mx-auto grid w-full max-w-[1100px] min-w-0 grid-cols-1 gap-4">
      <Link href="/guias" className="flex w-fit items-center gap-1.5 text-control text-muted-foreground hover:text-foreground">
        <ArrowLeftIcon weight={PESO_ICONO} className="size-3.5" />
        {t.form.volver}
      </Link>
      <AvisoExperimental />
      {seriesDeGuia.length === 0 ? (
        <p className="text-cuerpo text-muted-foreground" data-testid="guias-sin-serie">
          {t.pagina.sinSerie}{" "}
          <Link href="/series" className="text-primary hover:underline">
            {t.pagina.crearSerie}
          </Link>
        </p>
      ) : (
        <NuevaGuiaForm series={seriesDeGuia} establecimientos={establecimientos} catalogos={{ motivos, tiposDocumento, documentos }} hoy={hoyLima()} />
      )}
    </div>
  );
}
