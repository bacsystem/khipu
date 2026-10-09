import { ArrowLeftIcon, MailIcon } from "lucide-react";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { CuentaDetalle } from "@/components/admin/cuenta-detalle";
import { PagosDeCuenta } from "@/components/admin/pagos-de-cuenta";
import { PlanDeCuenta } from "@/components/admin/plan-de-cuenta";
import { getAdminServerSession } from "@/lib/admin-session-server";
import { esIdDeCuenta, hrefDetalleCuenta, obtenerCuentaAdmin } from "@/lib/api/admin-cuenta-detalle";
import { listarPagosDeCuenta } from "@/lib/api/admin-pagos";
import { obtenerPlanDeCuenta } from "@/lib/api/admin-plan-de-cuenta";
import { listarPlanesAdmin } from "@/lib/api/admin-planes";
import { ApiError } from "@/lib/api/types";
import { ACCION_SECUNDARIA } from "@/lib/estilos";
import { hoyLima } from "@/lib/formato";
import { correoDeSoporte, soporteParaMostrar } from "@/lib/soporte";
import { messages } from "@/lib/messages";

export const metadata = { title: "Cuenta · Backoffice" };

/**
 * Detalle de una cuenta (#181), solo lectura. Un id que no es un UUID ni siquiera llega al backend; un 404 del backend es «no existe».
 * Cualquier otro fallo muestra el error con «Reintentar» (la misma página). El 401 lo atiende el layout del panel, como en el listado.
 */
export default async function AdminCuentaPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!esIdDeCuenta(id)) notFound();

  const { access } = await getAdminServerSession();
  if (!access) redirect("/admin/login");

  const resultado = await obtenerCuentaAdmin(access, id).then(
    (cuenta) => ({ cuenta, error: null }),
    (error: unknown) => ({ cuenta: null, error }),
  );
  // Fuera del `.then`: `notFound` lanza, y no debe confundirse con un fallo del backend.
  if (resultado.error instanceof ApiError && resultado.error.status === 404) notFound();

  // El plan (#191) y los pagos (#194) van aparte del detalle y entre sí: si uno no carga, la ficha de la cuenta se ve igual y esa sección dice que falló, con su
  // «Reintentar». Los pagos se piden a la vez que el plan, pero no dependen de él: sin el plan solo se pierde la opción de extender el vencimiento.
  const [plan, pagos] = resultado.cuenta
    ? await Promise.all([
        Promise.all([obtenerPlanDeCuenta(access, id), listarPlanesAdmin(access)]).then(
          ([datos, planes]) => ({ datos, planes }),
          () => null,
        ),
        listarPagosDeCuenta(access, id).then(
          (p) => p,
          () => null,
        ),
      ])
    : [null, null];

  const soporte = soporteParaMostrar();
  const t = messages.admin.detalle;
  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <div className="grid gap-1">
        <Link href="/admin/cuentas" className="inline-flex w-fit items-center gap-1 text-xs text-primary hover:underline">
          <ArrowLeftIcon className="size-3" />
          {t.volver}
        </Link>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h1 className="font-heading text-2xl">{resultado.cuenta?.nombre ?? messages.admin.cuentas.titulo}</h1>
          {/* #250: el correo a soporte ya dice de qué cuenta se trata; solo nombre e id, nada sensible. */}
          {resultado.cuenta && soporte.email ? (
            <a href={correoDeSoporte(soporte.email, `Cuenta ${resultado.cuenta.nombre} (${id})`)} className={ACCION_SECUNDARIA}>
              <MailIcon className="size-4" aria-hidden />
              Escribir a soporte
            </a>
          ) : null}
        </div>
      </div>

      {resultado.cuenta ? (
        <CuentaDetalle
          cuenta={resultado.cuenta}
          hoy={hoyLima()}
          plan={
            <>
              {plan ? (
                <PlanDeCuenta cuentaId={id} cuentaNombre={resultado.cuenta.nombre} plan={plan.datos} planes={plan.planes} hoy={hoyLima()} />
              ) : (
                <div role="alert" className="flex flex-wrap items-center gap-3 rounded-xl border border-destructive-border bg-destructive/10 px-4 py-3 text-sm text-destructive">
                  <span>{messages.admin.planDeCuenta.error}</span>
                  <Link href={hrefDetalleCuenta(id)} className="font-medium underline">
                    {messages.admin.planDeCuenta.reintentar}
                  </Link>
                </div>
              )}
              {pagos ? (
                <PagosDeCuenta cuentaId={id} cuentaNombre={resultado.cuenta.nombre} pagos={pagos} plan={plan?.datos ?? null} hoy={hoyLima()} />
              ) : (
                <div role="alert" className="flex flex-wrap items-center gap-3 rounded-xl border border-destructive-border bg-destructive/10 px-4 py-3 text-sm text-destructive">
                  <span>{messages.admin.pagos.error}</span>
                  <Link href={hrefDetalleCuenta(id)} className="font-medium underline">
                    {messages.admin.pagos.reintentar}
                  </Link>
                </div>
              )}
            </>
          }
        />
      ) : (
        <div role="alert" className="flex flex-wrap items-center gap-3 rounded-xl border border-destructive-border bg-destructive/10 px-4 py-3 text-sm text-destructive">
          <span>{t.error}</span>
          <Link href={hrefDetalleCuenta(id)} className="font-medium underline">
            {messages.admin.cuentas.reintentar}
          </Link>
        </div>
      )}
    </div>
  );
}
