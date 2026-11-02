import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { getLocale, getMessages, getTranslations } from "next-intl/server";
import { CalendarIcon, ChartBarIcon, CreditCardIcon, GaugeIcon } from "@phosphor-icons/react/dist/ssr";
import { ListaDatos } from "khipu-design-system/datos/lista-datos";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { ProgresoLineal, type TonoProgreso } from "khipu-design-system/feedback/progreso";
import { CabeceraSeccion } from "khipu-design-system/patrones/cabecera-seccion";
import { Aviso } from "khipu-design-system/patrones/cabecera-dialogo";
import { PillEstado } from "khipu-design-system/patrones/pill-estado";
import { AyudaDeSoporte } from "@/components/soporte/ayuda-de-soporte";
import { interpolar } from "@/i18n/mensajes";
import { limitesEnPalabras, precioEnSoles } from "@/lib/api/admin-planes";
import { obtenerMiCuenta } from "@/lib/api/cuenta";
import { resumenDePlan } from "@/lib/cuenta/resumen-de-plan";
import { usoDelPlan } from "@/lib/cuenta/uso-del-plan";
import { CONTENEDOR_PAGINA, ETIQUETA_DATO, TARJETA } from "@/lib/estilos";
import { formatearFecha, formatearFechaDeLima, formatearMes, ultimoDiaCubierto } from "@/lib/formato";
import { getServerSession } from "@/lib/session-server";
import { cn } from "@/lib/utils";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.nav.items.plan} · ${messages.app.nombre}` };
}

/** El tono de la pill de cada estado de pago; su texto está en `messages.cuenta.plan.estados`. */
const ESTADO = {
  VIGENTE: "ok",
  EN_GRACIA: "aviso",
  VENCIDA: "error",
} as const;

/** El color de la barra de consumo según el tono del uso (verde de la paleta, ámbar al acercarse al tope, rojo pasado). */
const TONO_BARRA: Record<"ok" | "aviso" | "error", TonoProgreso> = { ok: "primary", aviso: "warning", error: "destructive" };

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
      <div className={CONTENEDOR_PAGINA}>
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
      ? { texto: t.estados[plan.estado as keyof typeof ESTADO], tono: ESTADO[plan.estado as keyof typeof ESTADO] }
      : { texto: plan.estado, tono: "neutro" as const };
  const pagadoHasta = plan.vence_en ? formatearFecha(ultimoDiaCubierto(plan.vence_en), locale) : null;
  const cubreHasta = plan.hasta_cuando_cubre ? formatearFecha(ultimoDiaCubierto(plan.hasta_cuando_cubre), locale) : null;

  return (
    <div className={CONTENEDOR_PAGINA}>
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

      <div className="grid grid-cols-1 items-start gap-3 lg:grid-cols-2">
        <section className={TARJETA} aria-labelledby="titulo-plan">
          <CabeceraSeccion
            icon={CreditCardIcon}
            titulo={<span id="titulo-plan">{interpolar(messages.nav.plan.etiqueta, { plan: plan.plan.nombre })}</span>}
            derecha={<PillEstado tono={estado.tono}>{estado.texto}</PillEstado>}
          />
          <div className="grid gap-3 px-4 pb-4">
            <ListaDatos
              datos={[
                { etiqueta: t.precio, valor: plan.plan.precio_mensual === 0 ? t.gratis : interpolar(t.alMes, { precio: precioEnSoles(plan.plan.precio_mensual, messages, locale) }) },
                { etiqueta: t.pagadoHasta, valor: pagadoHasta ?? t.noVence },
                ...(plan.vence_en ? [{ etiqueta: t.diasDeGracia, valor: plan.dias_de_gracia === 1 ? t.unDia : interpolar(t.dias, { n: plan.dias_de_gracia }) }] : []),
                ...(cubreHasta ? [{ etiqueta: t.seSirveHasta, valor: interpolar(t.graciaIncluida, { fecha: cubreHasta }) }] : []),
                { etiqueta: t.limites, valor: limitesEnPalabras(plan.plan.limites, messages) },
              ]}
            />
            <p className="text-control leading-relaxed text-muted-foreground">{t.retencionNota}</p>
            {plan.programado ? (
              <Aviso tono="neutro" icon={CalendarIcon}>
                {tr.rich("programado", {
                  fecha: formatearFechaDeLima(plan.programado.aplica_desde, locale),
                  plan: plan.programado.plan.nombre,
                  b: (texto) => <strong className="text-foreground">{texto}</strong>,
                })}
              </Aviso>
            ) : null}
          </div>
        </section>

        <section className={TARJETA} aria-labelledby="titulo-consumo">
          <CabeceraSeccion
            icon={GaugeIcon}
            titulo={<span id="titulo-consumo">{interpolar(t.consumoDe, { mes: formatearMes(consumo.mes, locale) })}</span>}
            derecha={
              <span className="font-mono text-cuerpo font-semibold text-foreground" data-testid="consumo-del-mes">
                {r.consumo}
              </span>
            }
          />
          <div className="grid gap-3 px-4 pb-4">
            {r.porcentaje !== null ? (
              <ProgresoLineal valor={r.porcentaje} tono={TONO_BARRA[r.tono]} etiqueta={<span className="sr-only">{t.consumoTope}</span>} mostrarValor={false} />
            ) : null}
            <p className="text-control leading-relaxed text-muted-foreground">{t.nota}</p>
          </div>
        </section>

        <section className={cn(TARJETA, "lg:col-span-2")} aria-labelledby="titulo-uso">
          <CabeceraSeccion icon={ChartBarIcon} titulo={<span id="titulo-uso">{t.usoTitulo}</span>} />
          <div className="grid gap-4 px-4 pb-4">
            <ul className="grid grid-cols-1 gap-5 sm:grid-cols-3">
              {uso.map((u) => (
                <li key={u.clave} data-testid={`uso-${u.clave}`} className="grid gap-2">
                  <div className="flex items-baseline justify-between gap-2">
                    <span className={ETIQUETA_DATO}>{t.uso[u.clave]}</span>
                    <span className="font-mono text-cuerpo font-semibold text-foreground">{u.texto}</span>
                  </div>
                  {u.porcentaje !== null ? (
                    <ProgresoLineal valor={u.porcentaje} tono={TONO_BARRA[u.tono]} etiqueta={<span className="sr-only">{t.uso[u.clave]}</span>} mostrarValor={false} />
                  ) : null}
                  {u.aviso ? <p className={cn("text-control leading-relaxed", u.tono === "error" ? "text-destructive-texto" : "text-muted-foreground")}>{u.aviso}</p> : null}
                </li>
              ))}
            </ul>
            <p className="text-control leading-relaxed text-muted-foreground">{t.usoNota}</p>
          </div>
        </section>
      </div>
    </div>
  );
}
