import Link from "next/link";
import { redirect } from "next/navigation";
import { Alerta } from "@/components/feedback/alerta";
import { limitesEnPalabras, precioEnSoles } from "@/lib/api/admin-planes";
import { obtenerMiCuenta } from "@/lib/api/cuenta";
import { resumenDePlan } from "@/lib/cuenta/resumen-de-plan";
import { ETIQUETA_DATO, TARJETA, TITULO_SECCION } from "@/lib/estilos";
import { formatearFecha, formatearFechaDeLima, formatearMes, ultimoDiaCubierto } from "@/lib/formato";
import { messages } from "@/lib/messages";
import { getServerSession } from "@/lib/session-server";
import { cn } from "@/lib/utils";

export const metadata = { title: `Plan y consumo · ${messages.app.nombre}` };

const ESTADO: Record<string, { texto: string; clase: string }> = {
  VIGENTE: { texto: "Al día", clase: "border-success-border bg-success text-success-foreground" },
  EN_GRACIA: { texto: "Pago vencido, en gracia", clase: "border-warning-border bg-warning text-warning-foreground" },
  VENCIDA: { texto: "Vencido", clase: "border-destructive/40 bg-destructive/10 text-destructive" },
};

function Dato({ etiqueta, children }: { etiqueta: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <span className={cn(ETIQUETA_DATO, "mb-1")}>{etiqueta}</span>
      <p className="text-[13px] text-foreground">{children}</p>
    </div>
  );
}

/**
 * El plan del cliente (C1): cuál tiene, hasta cuándo está pagado y cuánto consumió este mes contra su tope. Solo lectura: el plan lo cambia el equipo de khipu
 * (#191), así que la página dice a quién pedírselo en vez de ofrecer un botón que no puede hacer nada.
 */
export default async function PlanPage() {
  const { access } = await getServerSession();
  if (!access) redirect("/login");

  const cuenta = await obtenerMiCuenta(access).catch(() => null);
  if (!cuenta) {
    return (
      <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
        <Alerta tono="error" titulo="No se pudo leer tu plan">
          Puede ser un problema pasajero de conexión: tu plan y tu consumo no cambiaron. <Link href="/cuenta/plan" className="underline">Volver a intentar</Link>
        </Alerta>
      </div>
    );
  }

  const { plan, consumo } = cuenta;
  const r = resumenDePlan(cuenta);
  const estado = ESTADO[plan.estado] ?? ESTADO.VIGENTE;
  const pagadoHasta = plan.vence_en ? formatearFecha(ultimoDiaCubierto(plan.vence_en)) : null;
  const cubreHasta = plan.hasta_cuando_cubre ? formatearFecha(ultimoDiaCubierto(plan.hasta_cuando_cubre)) : null;

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <p className="text-sm text-muted-foreground">
        Tu plan, hasta cuándo está pagado y lo que llevas consumido este mes. Para cambiar de plan o renovarlo, comunícate con el equipo de khipu.
      </p>

      {r.aviso ? (
        <Alerta tono={r.tono === "error" ? "error" : "aviso"} titulo={r.aviso}>
          Para renovar tu plan o pasar a uno con más documentos, comunícate con el equipo de khipu.
        </Alerta>
      ) : null}

      <div className="grid grid-cols-1 items-start gap-4 lg:grid-cols-2">
        <section className={cn(TARJETA, "p-5")} aria-labelledby="titulo-plan">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
            <h2 id="titulo-plan" className={TITULO_SECCION}>
              Plan {plan.plan.nombre}
            </h2>
            <span className={cn("rounded-full border px-2.5 py-0.5 font-mono text-[11px] font-medium", estado.clase)}>{estado.texto}</span>
          </div>
          <div className="grid grid-cols-1 gap-4 pt-4 sm:grid-cols-2">
            <Dato etiqueta="Precio">{plan.plan.precio_mensual === 0 ? "Gratis" : `${precioEnSoles(plan.plan.precio_mensual)} al mes`}</Dato>
            <Dato etiqueta="Pagado hasta">{pagadoHasta ?? "No vence"}</Dato>
            {plan.vence_en ? <Dato etiqueta="Días de gracia">{plan.dias_de_gracia === 1 ? "1 día" : `${plan.dias_de_gracia} días`}</Dato> : null}
            {cubreHasta ? <Dato etiqueta="Se sirve hasta">{cubreHasta}, gracia incluida</Dato> : null}
            <div className="sm:col-span-2">
              <Dato etiqueta="Límites del plan">{limitesEnPalabras(plan.plan.limites)}</Dato>
            </div>
          </div>
          {plan.programado ? (
            <p className="mt-4 rounded-lg border border-border/80 bg-muted p-3 text-[12px] text-muted-foreground">
              Desde el {formatearFechaDeLima(plan.programado.aplica_desde)} pasas al plan <strong className="text-foreground">{plan.programado.plan.nombre}</strong>.
            </p>
          ) : null}
        </section>

        <section className={cn(TARJETA, "p-5")} aria-labelledby="titulo-consumo">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
            <h2 id="titulo-consumo" className={TITULO_SECCION}>
              Consumo de {formatearMes(consumo.mes)}
            </h2>
            <span className="font-mono text-[13px] font-semibold text-foreground" data-testid="consumo-del-mes">
              {r.consumo}
            </span>
          </div>
          {r.porcentaje !== null ? (
            <div className="mt-4 h-2 w-full overflow-hidden rounded-full bg-border" role="meter" aria-valuemin={0} aria-valuemax={100} aria-valuenow={r.porcentaje} aria-label="Consumo del tope del mes">
              <div
                className={cn("h-full rounded-full", r.tono === "ok" && "bg-primary", r.tono === "aviso" && "bg-warning-solid", r.tono === "error" && "bg-destructive")}
                style={{ width: `${r.porcentaje}%` }}
              />
            </div>
          ) : null}
          <p className="mt-3 text-[12px] leading-relaxed text-muted-foreground">
            Cuenta lo que SUNAT aceptó este mes en todas tus empresas, aunque después se anule, por su fecha de emisión (hora de Lima).
          </p>
        </section>
      </div>
    </div>
  );
}
