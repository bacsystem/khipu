import { CambiarPlan } from "@/components/admin/cambiar-plan";
import { Etiqueta, type Tono } from "@/components/admin/etiquetas";
import { Seccion } from "@/components/admin/seccion";
import { limitesEnPalabras, precioEnSoles, type PlanAdmin } from "@/lib/api/admin-planes";
import type { EstadoDeSuscripcionAdmin, PlanDeCuentaAdmin } from "@/lib/api/admin-plan-de-cuenta";
import { formatearFecha, formatearFechaDeLima, ultimoDiaCubierto } from "@/lib/formato";
import { messages } from "@/lib/messages";

const t = messages.admin.planDeCuenta;

const TONO: Record<EstadoDeSuscripcionAdmin, Tono> = { VIGENTE: "ok", EN_GRACIA: "aviso", VENCIDA: "error" };

function graciaEnPalabras(dias: number): string {
  if (dias === 0) return t.sinGracia;
  return dias === 1 ? t.graciaUno : t.graciaVarios.replace("{n}", String(dias));
}

/**
 * El plan de una cuenta en su ficha del backoffice (#191): cuál es, en qué estado de pago está, hasta qué día está pagado, cuántos días de gracia tiene y, si hay,
 * la bajada que espera el inicio del ciclo siguiente. «Pagado hasta» es el último día cubierto (el vencimiento es exclusivo). Solo se ofrecen los planes de la oferta.
 */
export function PlanDeCuenta({ cuentaId, cuentaNombre, plan, planes, hoy }: { cuentaId: string; cuentaNombre: string; plan: PlanDeCuentaAdmin; planes: PlanAdmin[]; hoy: string }) {
  const l = plan.plan.limites;
  return (
    <Seccion titulo={t.titulo} id="detalle-plan">
      <div data-testid="plan-de-cuenta" className="grid gap-3 p-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="grid gap-1">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-heading text-lg">{plan.plan.nombre}</span>
              <span data-testid="plan-estado" data-estado={plan.estado}>
                <Etiqueta tono={TONO[plan.estado]}>{t.estados[plan.estado]}</Etiqueta>
              </span>
            </div>
            <p className="text-[13px] text-muted-foreground">{t.precio.replace("{precio}", precioEnSoles(plan.plan.precio_mensual))}</p>
          </div>
          <CambiarPlan cuentaId={cuentaId} cuentaNombre={cuentaNombre} planActualId={plan.plan.id} planes={planes.filter((p) => p.estado === "ACTIVO")} hoy={hoy} />
        </div>

        <ul className="grid gap-0.5 text-[13px] text-foreground/90">
          <li>{plan.vence_en ? t.pagadoHasta.replace("{fecha}", formatearFecha(ultimoDiaCubierto(plan.vence_en))) : t.sinVencimiento}</li>
          {plan.vence_en ? <li>{graciaEnPalabras(plan.dias_de_gracia)}</li> : null}
          {plan.hasta_cuando_cubre ? <li>{t.cubreHasta.replace("{fecha}", formatearFecha(ultimoDiaCubierto(plan.hasta_cuando_cubre)))}</li> : null}
          <li className="text-muted-foreground">{t.desde.replace("{fecha}", formatearFechaDeLima(plan.inicia_en))}</li>
        </ul>

        <p className="text-[12px] text-muted-foreground">
          {t.limites.replace("{limites}", limitesEnPalabras(l))}
        </p>

        {plan.programado ? (
          <p data-testid="plan-programado" className="rounded-lg border border-warning-border bg-warning px-3 py-2 text-[12px] text-warning-foreground">
            {t.programado.replace("{plan}", plan.programado.plan.nombre).replace("{fecha}", formatearFechaDeLima(plan.programado.aplica_desde)).replace("{actual}", plan.plan.nombre)}
          </p>
        ) : null}
      </div>
    </Seccion>
  );
}
