import { ArrowLeftIcon } from "lucide-react";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { CuentaDetalle } from "@/components/admin/cuenta-detalle";
import { PlanDeCuenta } from "@/components/admin/plan-de-cuenta";
import { getAdminServerSession } from "@/lib/admin-session-server";
import { esIdDeCuenta, hrefDetalleCuenta, obtenerCuentaAdmin } from "@/lib/api/admin-cuenta-detalle";
import { obtenerPlanDeCuenta } from "@/lib/api/admin-plan-de-cuenta";
import { listarPlanesAdmin } from "@/lib/api/admin-planes";
import { ApiError } from "@/lib/api/types";
import { hoyLima } from "@/lib/formato";
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

  // El plan va aparte del detalle (#191): si no carga, la ficha de la cuenta se ve igual y el plan dice que falló, con su «Reintentar».
  const plan = resultado.cuenta
    ? await Promise.all([obtenerPlanDeCuenta(access, id), listarPlanesAdmin(access)]).then(
        ([datos, planes]) => ({ datos, planes }),
        () => null,
      )
    : null;

  const t = messages.admin.detalle;
  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <div className="grid gap-1">
        <Link href="/admin/cuentas" className="inline-flex w-fit items-center gap-1 text-xs text-primary hover:underline">
          <ArrowLeftIcon className="size-3" />
          {t.volver}
        </Link>
        <h1 className="font-heading text-2xl">{resultado.cuenta?.nombre ?? messages.admin.cuentas.titulo}</h1>
      </div>

      {resultado.cuenta ? (
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
          <CuentaDetalle cuenta={resultado.cuenta} hoy={hoyLima()} />
        </>
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
