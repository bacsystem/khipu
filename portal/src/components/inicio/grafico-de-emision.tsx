import { ChartBarIcon, WarningCircleIcon } from "@phosphor-icons/react/dist/ssr";
import { useLocale, useMessages } from "next-intl";
import { EstadoVacio } from "khipu-design-system/feedback/estado-vacio";
import { GraficoBarras } from "khipu-design-system/datos/grafico";
import { Acordeon } from "khipu-design-system/navegacion/acordeon";
import { CabeceraSeccion } from "khipu-design-system/patrones/cabecera-seccion";
import { Aviso } from "khipu-design-system/patrones/cabecera-dialogo";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import { CABECERA_TABLA, TARJETA } from "@/lib/estilos";
import type { Locale } from "@/i18n/config";
import { interpolar, type Mensajes } from "@/i18n/mensajes";
import { formatearFecha, formatearMonto } from "@/lib/formato";
import { monedasDeLaSerie, type PuntoDelDia } from "@/lib/inicio/panel";
import { cn } from "@/lib/utils";

function dia(fecha: string): number {
  return Number(fecha.slice(8, 10));
}

/** «5 Oct 2026: 3 comprobantes · S/ 1,180.00» — lo que dice el `title` de cada barra y lo que lee un lector de pantalla en la tabla. */
export function descripcionDelDia(p: PuntoDelDia, messages: Mensajes, locale: Locale): string {
  const t = messages.inicio.grafico;
  const comprobantes = interpolar(p.emitidos === 1 ? t.comprobanteUno : t.comprobanteVarios, { n: p.emitidos });
  const montos = Object.entries(p.facturado)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([moneda, total]) => formatearMonto(moneda, total, locale));
  return [`${formatearFecha(p.fecha, locale)}: ${comprobantes}`, ...montos].join(" · ");
}

/** El resumen que lee un lector de pantalla del gráfico entero: cuántos, cuántos días y el día de más emisión. */
export function resumenDelGrafico(serie: PuntoDelDia[], messages: Mensajes, locale: Locale): string {
  const t = messages.inicio.grafico;
  const total = serie.reduce((s, p) => s + p.emitidos, 0);
  const pico = serie.reduce((max, p) => (p.emitidos > max.emitidos ? p : max), serie[0]);
  const conEmision = serie.filter((p) => p.emitidos > 0).length;
  return interpolar(t.resumen, {
    emitidos: interpolar(total === 1 ? t.emitidoUno : t.emitidoVarios, { n: total }),
    conEmision,
    dias: serie.length,
    desde: formatearFecha(serie[0].fecha, locale),
    hasta: formatearFecha(serie[serie.length - 1].fecha, locale),
    pico: formatearFecha(pico.fecha, locale),
    picoEmitidos: pico.emitidos,
  });
}

/** El eje cuenta comprobantes: con pocos, la escala del gráfico pasa por 0.5 o 2.5, y esas líneas van sin número (no hay medio comprobante). */
function rotuloDeCantidad(v: number): string {
  return Number.isInteger(v) ? String(v) : "";
}

/** Qué días llevan rótulo en el eje: el primero, cada cinco y el último (hoy). En un teléfono 31 rótulos no caben. */
function conRotulo(i: number, total: number, fecha: string): boolean {
  return i === 0 || i === total - 1 || dia(fecha) % 5 === 0;
}

/**
 * Comprobantes emitidos por día del mes (#24): el `GraficoBarras` del design system (#511), plano dentro de la tarjeta de la sección, que lo nombra con su título. Una sola serie
 * (sin leyenda) y hoy, el último día, en el acento. El lector de pantalla oye el resumen del mes; cada día tiene su `title` (lo emitido y lo facturado por moneda) sobre una franja de
 * alto completo, el mismo texto del tooltip, y rótulo solo el primero, cada cinco y hoy. La misma información va como tabla, plegada. `serie` en `null`: no se pudo cargar.
 */
export function GraficoDeEmision({ serie, titulo }: { serie: PuntoDelDia[] | null; titulo: string }) {
  const messages = useMessages();
  const locale = useLocale() as Locale;
  const t = messages.inicio.grafico;
  const maximo = serie ? Math.max(0, ...serie.map((p) => p.emitidos)) : 0;
  const monedas = serie ? monedasDeLaSerie(serie) : [];

  return (
    <section aria-labelledby="grafico-emision-titulo" data-testid="grafico-emision" data-cargado={serie !== null} className={cn(TARJETA, "min-w-0")}>
      <CabeceraSeccion
        icon={ChartBarIcon}
        titulo={<span id="grafico-emision-titulo">{titulo}</span>}
        subtitulo={serie && maximo > 0 ? t.subtitulo : undefined}
      />

      <div className="grid min-w-0 gap-3 px-4 pb-4">
      {serie === null ? (
        <div data-testid="grafico-emision-error">
          <Aviso tono="neutro" icon={WarningCircleIcon}>
            {t.error}
          </Aviso>
        </div>
      ) : maximo === 0 ? (
        <div data-testid="grafico-emision-vacio">
          <EstadoVacio icon={ChartBarIcon} titulo={t.vacio} className="py-8" />
        </div>
      ) : (
        <>
          <div data-testid="grafico-emision-grafico" className="min-w-0">
            <GraficoBarras
              plano
              etiquetadoPor="grafico-emision-titulo"
              descripcion={resumenDelGrafico(serie, messages, locale)}
              categorias={serie.map((p) => String(dia(p.fecha)))}
              series={[{ nombre: t.emitidos, valores: serie.map((p) => p.emitidos) }]}
              destacado={serie.length - 1}
              alto={176}
              formato={rotuloDeCantidad}
              rotuloVisible={(i) => conRotulo(i, serie.length, serie[i].fecha)}
              tituloDeCategoria={(i) => descripcionDelDia(serie[i], messages, locale)}
              propiedadesDeCategoria={(i) => ({ "data-testid": "grafico-emision-dia", "data-fecha": serie[i].fecha, "data-emitidos": serie[i].emitidos })}
            />
          </div>

          <Acordeon
            items={[
              {
                id: "tabla",
                titulo: t.verTabla,
                contenido: (
                  <div className="min-w-0">
                    <Table data-testid="grafico-emision-tabla">
                      <TableHeader>
                        <TableRow className="hover:bg-transparent">
                          <TableHead className={`${CABECERA_TABLA} pl-3`}>{t.dia}</TableHead>
                          <TableHead className={`${CABECERA_TABLA} text-right`}>{t.emitidos}</TableHead>
                          {monedas.map((m) => (
                            <TableHead key={m} className={`${CABECERA_TABLA} text-right`}>
                              {interpolar(t.facturadoMoneda, { moneda: m })}
                            </TableHead>
                          ))}
                        </TableRow>
                      </TableHeader>
                      <TableBody className="text-control tabular-nums">
                        {serie.map((p) => (
                          <TableRow key={p.fecha}>
                            <TableCell className="py-1.5 pl-3">{formatearFecha(p.fecha, locale)}</TableCell>
                            <TableCell className="py-1.5 text-right">{p.emitidos}</TableCell>
                            {monedas.map((m) => (
                              <TableCell key={m} className="py-1.5 text-right">
                                {formatearMonto(m, p.facturado[m] ?? 0, locale)}
                              </TableCell>
                            ))}
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>
                ),
              },
            ]}
          />
        </>
      )}
      </div>
    </section>
  );
}
