import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { getLocale, getMessages, getTranslations } from "next-intl/server";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { AyudaDeSoporte } from "@/components/soporte/ayuda-de-soporte";
import { interpolar } from "@/i18n/mensajes";
import { limitesEnPalabras, precioEnSoles } from "@/lib/api/admin-planes";
import { obtenerMiCuenta } from "@/lib/api/cuenta";
import { resumenDePlan } from "@/lib/cuenta/resumen-de-plan";
import { usoDelPlan } from "@/lib/cuenta/uso-del-plan";
import { ETIQUETA_DATO, TARJETA, TITULO_SECCION } from "@/lib/estilos";
import { formatearFecha, formatearFechaDeLima, formatearMes, ultimoDiaCubierto } from "@/lib/formato";
import { getServerSession } from "@/lib/session-server";
import { cn } from "@/lib/utils";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.nav.items.plan} · ${messages.app.nombre}` };
}

/** El color de cada estado de pago; su texto está en `messages.cuenta.plan.estados`. */
const ESTADO = {
  VIGENTE: "border-success-border bg-success text-success-foreground",
  EN_GRACIA: "border-warning-border bg-warning text-warning-foreground",
  VENCIDA: "border-destructive/40 bg-destructive-suave text-destructive-texto",
} as const;

function Dato({ etiqueta, children }: { etiqueta: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <span className={cn(ETIQUETA_DATO, "mb-1")}>{etiqueta}</span>
      <p className="text-control text-foreground">{children}</p>
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

  const messages = await getMessages();
  const locale = await getLocale();
  const t = messages.cuenta.plan;
  const tr = await getTranslations("cuenta.plan");

  const cuenta = await obtenerMiCuenta(access).catch(() => null);
  if (!cuenta) {
    return (
      <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
        <Alerta tono="error" titulo={t.errorTitulo}>
          {tr.rich("errorDetalle", {
            link: (texto) => (
              <Link href="/cuenta/plan" className="underline">
                {texto}
              </Link>
            ),
          })}
        </Alerta>
      </div>
    );
  }

  const { plan, consumo } = cuenta;
  const r = resumenDePlan(cuenta, messages, locale);
  const uso = usoDelPlan(cuenta, messages);
  const avisosDeUso = uso.filter((u) => u.aviso !== null);
  // Un estado que el portal no conoce se muestra tal cual, en neutro: caer en «Al día» afirmaría lo que no se sabe (265-H4).
  const estado =
    plan.estado in ESTADO
      ? { texto: t.estados[plan.estado as keyof typeof ESTADO], clase: ESTADO[plan.estado as keyof typeof ESTADO] }
      : { texto: plan.estado, clase: "border-border bg-muted text-muted-foreground" };
  const pagadoHasta = plan.vence_en ? formatearFecha(ultimoDiaCubierto(plan.vence_en), locale) : null;
  const cubreHasta = plan.hasta_cuando_cubre ? formatearFecha(ultimoDiaCubierto(plan.hasta_cuando_cubre), locale) : null;

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <p className="text-cuerpo text-muted-foreground">{t.descripcion}</p>
      {/* #250: «comunícate» tiene que decir por dónde. */}
      <AyudaDeSoporte />

      {r.aviso ? (
        <Alerta tono={r.tono === "error" ? "error" : "aviso"} titulo={r.aviso}>
          {t.avisoRenovar}
        </Alerta>
      ) : null}

      {/* #498: solo lo que se pasó del tope (una cuenta a la que le bajaron el plan) sube a una alerta. Estar justo en el tope (1 de 1 en el plan Gratis) es el estado normal de
          casi toda cuenta nueva: lo dicen la barra y la línea de abajo, no tres banners amarillos en cada visita. */}
      {avisosDeUso
        .filter((u) => u.tono === "error")
        .map((u) => (
          <Alerta key={u.clave} tono="error" titulo={t.uso[u.clave]}>
            {u.aviso}
          </Alerta>
        ))}

      <div className="grid grid-cols-1 items-start gap-4 lg:grid-cols-2">
        <section className={cn(TARJETA, "p-5")} aria-labelledby="titulo-plan">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
            <h2 id="titulo-plan" className={TITULO_SECCION}>
              {interpolar(messages.nav.plan.etiqueta, { plan: plan.plan.nombre })}
            </h2>
            <span className={cn("rounded-full border px-2.5 py-0.5 font-mono text-etiqueta font-medium", estado.clase)}>{estado.texto}</span>
          </div>
          <div className="grid grid-cols-1 gap-4 pt-4 sm:grid-cols-2">
            <Dato etiqueta={t.precio}>{plan.plan.precio_mensual === 0 ? t.gratis : interpolar(t.alMes, { precio: precioEnSoles(plan.plan.precio_mensual, messages, locale) })}</Dato>
            <Dato etiqueta={t.pagadoHasta}>{pagadoHasta ?? t.noVence}</Dato>
            {plan.vence_en ? <Dato etiqueta={t.diasDeGracia}>{plan.dias_de_gracia === 1 ? t.unDia : interpolar(t.dias, { n: plan.dias_de_gracia })}</Dato> : null}
            {cubreHasta ? <Dato etiqueta={t.seSirveHasta}>{interpolar(t.graciaIncluida, { fecha: cubreHasta })}</Dato> : null}
            <div className="sm:col-span-2">
              <Dato etiqueta={t.limites}>{limitesEnPalabras(plan.plan.limites, messages)}</Dato>
              <p className="mt-2 text-control leading-relaxed text-muted-foreground">{t.retencionNota}</p>
            </div>
          </div>
          {plan.programado ? (
            <p className="mt-4 rounded-lg border border-border/80 bg-muted p-3 text-control text-muted-foreground">
              {tr.rich("programado", {
                fecha: formatearFechaDeLima(plan.programado.aplica_desde, locale),
                plan: plan.programado.plan.nombre,
                b: (texto) => <strong className="text-foreground">{texto}</strong>,
              })}
            </p>
          ) : null}
        </section>

        <section className={cn(TARJETA, "p-5")} aria-labelledby="titulo-consumo">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
            <h2 id="titulo-consumo" className={TITULO_SECCION}>
              {interpolar(t.consumoDe, { mes: formatearMes(consumo.mes, locale) })}
            </h2>
            <span className="font-mono text-cuerpo font-semibold text-foreground" data-testid="consumo-del-mes">
              {r.consumo}
            </span>
          </div>
          {r.porcentaje !== null ? (
            <div className="mt-4 h-2 w-full overflow-hidden rounded-full bg-border" role="meter" aria-valuemin={0} aria-valuemax={100} aria-valuenow={r.porcentaje} aria-label={t.consumoTope}>
              <div
                className={cn("h-full rounded-full", r.tono === "ok" && "bg-primary", r.tono === "aviso" && "bg-warning-solid", r.tono === "error" && "bg-destructive")}
                style={{ width: `${r.porcentaje}%` }}
              />
            </div>
          ) : null}
          <p className="mt-3 text-control leading-relaxed text-muted-foreground">{t.nota}</p>
        </section>

        <section className={cn(TARJETA, "p-5", "lg:col-span-2")} aria-labelledby="titulo-uso">
          <h2 id="titulo-uso" className={cn(TITULO_SECCION, "border-b border-border/60 pb-3")}>
            {t.usoTitulo}
          </h2>
          <ul className="mt-4 grid grid-cols-1 gap-5 sm:grid-cols-3">
            {uso.map((u) => (
              <li key={u.clave} data-testid={`uso-${u.clave}`}>
                <div className="flex items-baseline justify-between gap-2">
                  <span className={ETIQUETA_DATO}>{t.uso[u.clave]}</span>
                  <span className="font-mono text-cuerpo font-semibold text-foreground">{u.texto}</span>
                </div>
                {u.porcentaje !== null ? (
                  <div className="mt-2 h-2 w-full overflow-hidden rounded-full bg-border" role="meter" aria-valuemin={0} aria-valuemax={100} aria-valuenow={u.porcentaje} aria-label={t.uso[u.clave]}>
                    <div
                      className={cn("h-full rounded-full", u.tono === "ok" && "bg-primary", u.tono === "aviso" && "bg-warning-solid", u.tono === "error" && "bg-destructive")}
                      style={{ width: `${u.porcentaje}%` }}
                    />
                  </div>
                ) : null}
                {u.aviso ? <p className={cn("mt-2 text-control leading-relaxed", u.tono === "error" ? "text-destructive" : "text-muted-foreground")}>{u.aviso}</p> : null}
              </li>
            ))}
          </ul>
          <p className="mt-4 text-control leading-relaxed text-muted-foreground">{t.usoNota}</p>
        </section>
      </div>
    </div>
  );
}
