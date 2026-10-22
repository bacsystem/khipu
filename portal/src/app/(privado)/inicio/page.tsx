import { ReceiptTextIcon } from "lucide-react";
import Link from "next/link";
import { GuiaParaEmitir } from "@/components/empresa/guia-para-emitir";
import { EstadoVacio } from "@/components/feedback/estado-vacio";
import { AlertasDelPanel } from "@/components/inicio/alertas-del-panel";
import { GraficoDeEmision } from "@/components/inicio/grafico-de-emision";
import { KpisDelMes } from "@/components/inicio/kpis-del-mes";
import { obtenerEmpresaActual } from "@/lib/api/empresas";
import { emisionPorDia, resumirFacturas } from "@/lib/api/facturas";
import { listarSeries } from "@/lib/api/series";
import { hayPasosPendientes, pasosParaEmitir } from "@/lib/empresa/guia-emision";
import { ACCION_SECUNDARIA } from "@/lib/estilos";
import { hoyLima } from "@/lib/formato";
import { alertasDelPanel, esEmpresaNueva, mesEnCurso, mismoTramoDelMesAnterior, serieDiaria, sinLoQueYaDiceLaGuia, ventanaDeAtencion } from "@/lib/inicio/panel";
import { messages } from "@/lib/messages";
import { getServerSession } from "@/lib/session-server";

export const metadata = { title: `Inicio · ${messages.app.nombre}` };

const MESES = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "setiembre", "octubre", "noviembre", "diciembre"];

/** «Octubre de 2026, del 1 al 20» (o «el 1» el primer día del mes). */
function mesEnPalabras(hoy: string): string {
  const [anio, mes, dia] = hoy.split("-").map(Number);
  const nombre = MESES[mes - 1];
  return `${nombre[0].toUpperCase()}${nombre.slice(1)} de ${anio}, ${dia === 1 ? "el 1" : `del 1 al ${dia}`}`;
}

/**
 * Medido con 100 000 comprobantes reales, compilado: ~80 ms el HTML del servidor y ~110 ms hasta `load` en el navegador (k6/README.md, «Panel de inicio»).
 *
 * El panel de inicio (#24): adonde se llega al entrar. Lo del mes en curso (Lima) en una mirada: indicadores, emisión por día y avisos con su enlace. Las lecturas (cinco; seis del 1 al 7, ver `ventanaDeAtencion`) van en
 * paralelo y cada una falla sola: si una no carga, su sección lo dice y el resto se muestra igual.
 */
export default async function InicioPage() {
  const { access, empresaId } = await getServerSession();
  if (!access || !empresaId) {
    return (
      <p className="text-sm text-muted-foreground">
        Primero registra una empresa para ver tu panel.{" "}
        <Link href="/onboarding" className="text-primary hover:underline">
          Configurar empresa
        </Link>
      </p>
    );
  }

  const hoy = hoyLima();
  const mes = mesEnCurso(hoy);
  const anterior = mismoTramoDelMesAnterior(hoy);
  // Los avisos de comprobantes miran al menos los últimos 7 días (ver `ventanaDeAtencion`); del 8 en adelante es el mes y se reutiliza su resumen.
  const ventana = ventanaDeAtencion(hoy);
  const atencionAparte = ventana.desde !== mes.desde;
  const [resumen, resumenAnterior, emision, empresa, series, resumenDeLaVentana] = await Promise.all([
    resumirFacturas(access, empresaId, mes).catch(() => null),
    resumirFacturas(access, empresaId, anterior).catch(() => null),
    emisionPorDia(access, empresaId, mes).catch(() => null),
    obtenerEmpresaActual(access, empresaId).catch(() => null),
    // `null` = no se pudo leer: la guía no afirma que falten series.
    listarSeries(access, empresaId).catch(() => null),
    atencionAparte ? resumirFacturas(access, empresaId, ventana).catch(() => null) : null,
  ]);
  const resumenDeAtencion = atencionAparte ? resumenDeLaVentana : resumen;

  const pasos = empresa ? pasosParaEmitir(empresa, series, hoy) : null;
  const alertas = sinLoQueYaDiceLaGuia(alertasDelPanel(empresa, resumenDeAtencion, ventana, hoy), pasos);
  const guiaPendiente = pasos !== null && hayPasosPendientes(pasos);
  // Solo una empresa que nunca emitió (series en 0) ve la invitación en vez de indicadores: una con historia que hoy no emitió nada sigue viendo los suyos.
  const sinEmision = esEmpresaNueva(series) && (resumen === null || resumen.emitidos === 0);

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4" data-testid="panel-de-inicio">
      <p className="text-[13px] text-muted-foreground" data-testid="panel-periodo">
        {mesEnPalabras(hoy)}
        {empresa ? <span className="text-muted-foreground/80"> · {empresa.razon_social}</span> : null}
      </p>

      {pasos ? <GuiaParaEmitir pasos={pasos} /> : null}

      <AlertasDelPanel alertas={alertas} incompleto={empresa === null || resumenDeAtencion === null} guiaPendiente={guiaPendiente} />

      {sinEmision ? (
        <EstadoVacio
          icon={ReceiptTextIcon}
          titulo="Aún no emites comprobantes"
          accion={
            <Link href="/comprobantes" className={ACCION_SECUNDARIA}>
              Ir a Comprobantes
            </Link>
          }
        >
          {guiaPendiente
            ? "Completa los pasos de «Para empezar a emitir» y emite tu primer comprobante con «Nuevo comprobante», arriba a la derecha."
            : "Emite tu primer comprobante con «Nuevo comprobante», arriba a la derecha, o desde tu sistema con la API. Aquí verás cómo va el mes."}
        </EstadoVacio>
      ) : (
        <>
          <KpisDelMes resumen={resumen} anterior={resumenAnterior} />
          <GraficoDeEmision serie={emision ? serieDiaria(emision, mes) : null} titulo="Emisión por día" />
        </>
      )}
    </div>
  );
}
