"use client";

import { PowerIcon, PowerOffIcon, Trash2Icon } from "lucide-react";
import { DialogoDeAccion } from "@/components/admin/dialogo-de-accion";
import { FormularioDePlan } from "@/components/admin/formulario-de-plan";
import type { PlanAdmin } from "@/lib/api/admin-planes";
import { messages } from "@/lib/messages";

const t = messages.admin.planes;

/** Lo que pasa con las cuentas que ya tienen el plan al desactivarlo: nada. El diálogo lo dice con la cifra, porque es lo que el administrador quiere saber. */
function frasePorCuentas(cuentas: number): string {
  if (cuentas === 0) return t.desactivar.cuentasNinguna;
  if (cuentas === 1) return t.desactivar.cuentasUna;
  return t.desactivar.cuentasVarias.replace("{n}", String(cuentas));
}

/**
 * Las acciones de una fila de la tabla de planes (#190). Solo se ofrece lo que el plan permite: el de las cuentas nuevas no se desactiva ni se borra, un plan con
 * cuentas se desactiva pero no se borra, y uno inactivo se puede volver a ofrecer. Que se ofrezca no es lo que lo autoriza: el backend lo vuelve a decidir (y un
 * plan que tuvo cuentas alguna vez tampoco se borra, aunque hoy no tenga ninguna).
 */
export function AccionesDePlan({ plan }: { plan: PlanAdmin }) {
  const activo = plan.estado === "ACTIVO";
  const id = plan.id;

  return (
    <div className="flex flex-wrap items-center justify-end gap-1.5">
      <FormularioDePlan plan={plan} />
      {activo && !plan.por_defecto ? (
        <DialogoDeAccion
          testId={`plan-desactivar-${id}`}
          boton={t.desactivar.boton}
          icono={PowerOffIcon}
          chico
          titulo={t.desactivar.titulo.replace("{nombre}", plan.nombre)}
          descripcion={t.desactivar.descripcion}
          efectos={[t.desactivar.efectos[0], frasePorCuentas(plan.cuentas), t.desactivar.efectos[1]]}
          advertencia
          confirmar={t.desactivar.confirmar}
          enviando={t.desactivar.enviando}
          cancelar={t.desactivar.cancelar}
          ruta={`/api/admin/planes/${id}/desactivar`}
          estadoViejo={["PLAN_YA_INACTIVO", "PLAN_POR_DEFECTO", "NO_ENCONTRADO"]}
        />
      ) : null}
      {!activo ? (
        <DialogoDeAccion
          testId={`plan-activar-${id}`}
          boton={t.activar.boton}
          icono={PowerIcon}
          chico
          tono="primario"
          titulo={t.activar.titulo.replace("{nombre}", plan.nombre)}
          descripcion={t.activar.descripcion}
          efectos={t.activar.efectos}
          confirmar={t.activar.confirmar}
          enviando={t.activar.enviando}
          cancelar={t.activar.cancelar}
          ruta={`/api/admin/planes/${id}/activar`}
          estadoViejo={["PLAN_YA_ACTIVO", "NO_ENCONTRADO"]}
        />
      ) : null}
      {plan.cuentas === 0 && !plan.por_defecto ? (
        <DialogoDeAccion
          testId={`plan-borrar-${id}`}
          boton={t.eliminar.boton}
          icono={Trash2Icon}
          chico
          tono="peligro"
          titulo={t.eliminar.titulo.replace("{nombre}", plan.nombre)}
          descripcion={t.eliminar.descripcion}
          efectos={t.eliminar.efectos}
          advertencia
          confirmar={t.eliminar.confirmar}
          enviando={t.eliminar.enviando}
          cancelar={t.eliminar.cancelar}
          ruta={`/api/admin/planes/${id}`}
          metodo="DELETE"
          estadoViejo={["PLAN_EN_USO", "PLAN_POR_DEFECTO", "NO_ENCONTRADO"]}
        />
      ) : null}
    </div>
  );
}
