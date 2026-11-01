import Link from "next/link";
import { EstablecimientosTable } from "@/components/establecimientos/establecimientos-table";
import { Metrica } from "@/components/compartidos/metrica";
import { listarEstablecimientos } from "@/lib/api/establecimientos";
import { listarSeries } from "@/lib/api/series";
import { getServerSession } from "@/lib/session-server";
import type { Metadata } from "next";
import { getMessages } from "next-intl/server";
import { interpolar } from "@/i18n/mensajes";
import { FilaMetricas } from "khipu-design-system/patrones/metricas";
import { ListNumbersIcon, MapPinIcon, ReceiptIcon, StorefrontIcon } from "@phosphor-icons/react/dist/ssr";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.nav.items.establecimientos} · ${messages.app.nombre}` };
}

export default async function EstablecimientosPage() {
  const { access, empresaId } = await getServerSession();
  const t = (await getMessages()).establecimientos.pagina;

  if (!access || !empresaId) {
    return (
      <p className="text-cuerpo text-muted-foreground">
        {t.sinEmpresa}{" "}
        <Link href="/onboarding" className="text-primary hover:underline">
          {t.configurarEmpresa}
        </Link>
      </p>
    );
  }

  const [establecimientos, series] = await Promise.all([listarEstablecimientos(access, empresaId), listarSeries(access, empresaId)]);
  const principal = establecimientos.find((e) => e.principal);
  const anexos = establecimientos.filter((e) => !e.principal);
  const activos = anexos.filter((e) => e.activo).length;
  const seriesEnAnexos = series.filter((s) => s.activa && (s.establecimiento ?? "0000") !== "0000").length;

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-3">
      <FilaMetricas>
        <Metrica
          etiqueta={t.domicilioFiscal} icono={MapPinIcon} tono={principal ? "ok" : "aviso"}
          ayuda={
            principal ? (
              <span className="truncate">{principal.domicilio.direccion}</span>
            ) : (
              <span className="flex items-center gap-1 text-warning-foreground">
                <span className="size-1.5 shrink-0 rounded-full bg-warning-solid" />
                {t.sinConfigurar}
              </span>
            )
          }
        >
          <span className="font-mono">0000</span>
        </Metrica>
        <Metrica etiqueta={t.anexosActivos} icono={StorefrontIcon} tono="acento" ayuda={anexos.length - activos > 0 ? interpolar(t.dadosDeBaja, { cantidad: anexos.length - activos }) : t.anexosAyuda}>
          {activos}
          <span className="text-control font-normal text-muted-foreground">{interpolar(t.registrados, { total: anexos.length })}</span>
        </Metrica>
        <Metrica etiqueta={t.seriesEnAnexos} icono={ListNumbersIcon} tono="neutro" ayuda={seriesEnAnexos > 0 ? t.seriesEnAnexosAyuda : t.seriesEnFiscal}>
          {seriesEnAnexos}
          <span className="text-control font-normal text-muted-foreground">{interpolar(t.activas, { total: series.filter((s) => s.activa).length })}</span>
        </Metrica>
        <Metrica etiqueta={t.enElXml} icono={ReceiptIcon} tono="neutro" ayuda={t.enElXmlAyuda}>
          {/* eslint-disable-next-line khipu/no-literal-de-ui -- nombre del elemento UBL, no se traduce */}
          <span className="text-primary">AddressTypeCode</span>
        </Metrica>
      </FilaMetricas>

      <EstablecimientosTable establecimientos={establecimientos} series={series} />
    </div>
  );
}
