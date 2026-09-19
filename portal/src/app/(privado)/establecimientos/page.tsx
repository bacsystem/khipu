import Link from "next/link";
import { EstablecimientosTable } from "@/components/establecimientos/establecimientos-table";
import { Metrica } from "@/components/ui/metrica";
import { listarEstablecimientos } from "@/lib/api/establecimientos";
import { listarSeries } from "@/lib/api/series";
import { getServerSession } from "@/lib/session-server";

export default async function EstablecimientosPage() {
  const { access, empresaId } = await getServerSession();

  if (!access || !empresaId) {
    return (
      <p className="text-sm text-muted-foreground">
        Configura primero una empresa para registrar establecimientos.{" "}
        <Link href="/onboarding" className="text-primary hover:underline">
          Configurar empresa
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
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <section className="grid grid-cols-2 gap-x-5 gap-y-4 rounded-xl border border-border bg-card px-5 py-3 shadow-xs lg:grid-cols-4 lg:divide-x lg:divide-border lg:[&>*:not(:first-child)]:pl-5">
        <Metrica
          etiqueta="Domicilio fiscal"
          ayuda={
            principal ? (
              <span className="truncate">{principal.domicilio.direccion}</span>
            ) : (
              <span className="flex items-center gap-1 text-warning-foreground">
                <span className="size-1.5 shrink-0 rounded-full bg-warning-solid" />
                Sin configurar: el XML solo lleva el 0000
              </span>
            )
          }
        >
          <span className="font-mono">0000</span>
        </Metrica>
        <Metrica etiqueta="Anexos activos" ayuda={anexos.length - activos > 0 ? `${anexos.length - activos} dado(s) de baja` : "Sucursales, tiendas y almacenes"}>
          {activos}
          <span className="text-[12px] font-normal text-muted-foreground">/ {anexos.length} registrados</span>
        </Metrica>
        <Metrica etiqueta="Series en anexos" ayuda={seriesEnAnexos > 0 ? "Emiten con la dirección de su anexo" : "Todas las series emiten desde el domicilio fiscal"}>
          {seriesEnAnexos}
          <span className="text-[12px] font-normal text-muted-foreground">/ {series.filter((s) => s.activa).length} activas</span>
        </Metrica>
        <Metrica etiqueta="En el XML" ayuda="cac:RegistrationAddress del emisor, según la serie">
          <span className="text-primary">AddressTypeCode</span>
        </Metrica>
      </section>

      <EstablecimientosTable establecimientos={establecimientos} series={series} />
    </div>
  );
}
