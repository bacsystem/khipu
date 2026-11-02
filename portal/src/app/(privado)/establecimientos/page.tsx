import { SinEmpresa } from "@/components/compartidos/sin-empresa";
import { EstablecimientosTable } from "@/components/establecimientos/establecimientos-table";
import { Metrica } from "@/components/compartidos/metrica";
import { listarEstablecimientos } from "@/lib/api/establecimientos";
import { listarSeries } from "@/lib/api/series";
import { getServerSession } from "@/lib/session-server";
import type { Metadata } from "next";
import { getMessages } from "next-intl/server";
import { interpolar } from "@/i18n/mensajes";
import { AyudaMetrica, FilaMetricas } from "khipu-design-system/patrones/metricas";
import { CONTENEDOR_PAGINA } from "@/lib/estilos";
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
      <SinEmpresa texto={t.sinEmpresa} accion={t.configurarEmpresa} />
    );
  }

  const [establecimientos, series] = await Promise.all([listarEstablecimientos(access, empresaId), listarSeries(access, empresaId)]);
  const principal = establecimientos.find((e) => e.principal);
  const anexos = establecimientos.filter((e) => !e.principal);
  const activos = anexos.filter((e) => e.activo).length;
  const seriesEnAnexos = series.filter((s) => s.activa && (s.establecimiento ?? "0000") !== "0000").length;

  return (
    <div className={CONTENEDOR_PAGINA}>
      <FilaMetricas>
        <Metrica
          etiqueta={t.domicilioFiscal} icono={MapPinIcon} tono={principal ? "ok" : "aviso"}
          ayuda={
            principal ? (
              <span className="truncate">{principal.domicilio.direccion}</span>
            ) : (
              <AyudaMetrica tono="aviso">{t.sinConfigurar}</AyudaMetrica>
            )
          }
        >
          <span className="font-mono">0000</span>
        </Metrica>
        <Metrica etiqueta={t.anexosActivos} icono={StorefrontIcon} tono="acento" ayuda={anexos.length - activos > 0 ? interpolar(t.dadosDeBaja, { cantidad: anexos.length - activos }) : t.anexosAyuda} sufijo={interpolar(t.registrados, { total: anexos.length })}>
          {activos}
        </Metrica>
        <Metrica etiqueta={t.seriesEnAnexos} icono={ListNumbersIcon} tono="neutro" ayuda={seriesEnAnexos > 0 ? t.seriesEnAnexosAyuda : t.seriesEnFiscal} sufijo={interpolar(t.activas, { total: series.filter((s) => s.activa).length })}>
          {seriesEnAnexos}
        </Metrica>
        <Metrica etiqueta={t.enElXml} icono={ReceiptIcon} tono="neutro" ayuda={t.enElXmlAyuda} texto>
          {/* eslint-disable-next-line khipu/no-literal-de-ui -- nombre del elemento UBL, no se traduce */}
          <span className="text-primary">AddressTypeCode</span>
        </Metrica>
      </FilaMetricas>

      <EstablecimientosTable establecimientos={establecimientos} series={series} />
    </div>
  );
}
