import { InboxIcon } from "lucide-react";
import { AccionesDePlan } from "@/components/admin/acciones-de-plan";
import { Etiqueta } from "@/components/admin/etiquetas";
import { FormularioDePlan } from "@/components/admin/formulario-de-plan";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { cambiosDeLimites, limiteEnPalabras, precioEnSoles, retencionEnPalabras, type PlanAdmin } from "@/lib/api/admin-planes";
import { CABECERA_TABLA } from "@/lib/estilos";
import { formatearFechaDeLima } from "@/lib/formato";
import { messages } from "@/lib/messages";
import { cn } from "@/lib/utils";

const t = messages.admin.planes;
const columnas = t.columnas;

/** Lo que va a cambiar en un plan y desde cuándo, para mostrarlo aparte de lo vigente: todavía no manda. */
function CambioProgramado({ plan }: { plan: PlanAdmin }) {
  const programado = plan.limites_programados;
  if (!programado) return null;
  const cambios = cambiosDeLimites(plan.limites, programado.limites);
  if (cambios.length === 0) return null;
  return (
    <p data-testid={`plan-programado-${plan.id}`} title={t.programadoAyuda} className="mt-1 text-[11px] leading-relaxed text-warning-foreground">
      <span className="font-medium">{t.programado.replace("{fecha}", formatearFechaDeLima(programado.aplica_desde))}</span>
      {": "}
      {cambios.map((c) => `${t.cambiosDe[c.campo]}: ${c.de} → ${c.a}`).join(" · ")}
    </p>
  );
}

/**
 * Listado de planes del backoffice (#190). Los límites que se ven son los que mandan en el ciclo en curso; un cambio ya decidido pero que todavía no entra se
 * muestra debajo del nombre, con qué cambia y desde cuándo. El backend entrega los planes ya ordenados del más barato al más caro.
 */
export function PlanesTabla({ planes }: { planes: PlanAdmin[] }) {
  return (
    <div className="grid min-w-0 grid-cols-1 gap-4">
      <div className="flex justify-end">
        <FormularioDePlan />
      </div>

      <div className="overflow-hidden rounded-xl border border-border/90 bg-card shadow-2xs">
        <Table>
          <TableHeader>
            <TableRow className="border-b border-border/80 bg-muted hover:bg-muted">
              <TableHead className={`${CABECERA_TABLA} pl-4`}>{columnas.plan}</TableHead>
              <TableHead className={`${CABECERA_TABLA} text-right`}>{columnas.precio}</TableHead>
              <TableHead className={`${CABECERA_TABLA} text-right`}>{columnas.documentos}</TableHead>
              <TableHead className={`${CABECERA_TABLA} text-right`}>{columnas.rucs}</TableHead>
              <TableHead className={`${CABECERA_TABLA} text-right`}>{columnas.usuarios}</TableHead>
              <TableHead className={`${CABECERA_TABLA} text-right`}>{columnas.apiKeys}</TableHead>
              <TableHead className={`${CABECERA_TABLA} text-right`}>{columnas.retencion}</TableHead>
              <TableHead className={`${CABECERA_TABLA} text-right`}>{columnas.cuentas}</TableHead>
              <TableHead className={`${CABECERA_TABLA} pr-4 text-right`}>{columnas.acciones}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-[13px]">
            {planes.map((p) => (
              <TableRow
                key={p.id}
                data-testid={`plan-fila-${p.id}`}
                data-estado={p.estado}
                className={cn("border-b border-border/60 hover:bg-muted/80", p.estado === "INACTIVO" && "bg-muted/40 text-muted-foreground")}
              >
                <TableCell className="py-2 pr-3 pl-4 whitespace-normal">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <span className="font-medium text-foreground">{p.nombre}</span>
                    {p.por_defecto ? (
                      <span title={t.porDefectoAyuda}>
                        <Etiqueta tono="neutro">{t.porDefecto}</Etiqueta>
                      </span>
                    ) : null}
                    {p.estado === "INACTIVO" ? <Etiqueta tono="aviso">{t.inactivo}</Etiqueta> : null}
                  </div>
                  <CambioProgramado plan={p} />
                </TableCell>
                <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{precioEnSoles(p.precio_mensual)}</TableCell>
                <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{limiteEnPalabras(p.limites.documentos_al_mes)}</TableCell>
                <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{p.limites.rucs}</TableCell>
                <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{limiteEnPalabras(p.limites.usuarios)}</TableCell>
                <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{limiteEnPalabras(p.limites.api_keys)}</TableCell>
                <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{retencionEnPalabras(p.limites.retencion_anios)}</TableCell>
                <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{p.cuentas}</TableCell>
                <TableCell className="py-2 pr-4 pl-3">
                  <AccionesDePlan plan={p} />
                </TableCell>
              </TableRow>
            ))}
            {planes.length === 0 ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={9} className="py-14 text-center">
                  <div className="flex flex-col items-center gap-2 text-muted-foreground">
                    <InboxIcon className="size-6" />
                    <p className="text-sm">{t.vacio}</p>
                  </div>
                </TableCell>
              </TableRow>
            ) : null}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
