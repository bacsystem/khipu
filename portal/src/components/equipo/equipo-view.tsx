"use client";

import { ArrowCounterClockwiseIcon, EnvelopeSimpleIcon, PaperPlaneTiltIcon, UsersIcon } from "@phosphor-icons/react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useLocale, useMessages } from "next-intl";
import { SelectCampo } from "khipu-design-system/formularios/select-campo";
import { useState, useTransition } from "react";
import { CONTENEDOR_TABLA, FilaVacia } from "khipu-design-system/patrones/pie-tabla";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import { apiRequest } from "@/lib/api/browser";
import { ROLES, type InvitacionAlEquipo, type InvitacionEnviada, type MiembroDelEquipo, type Rol } from "@/lib/api/equipo";
import { ACCION_SECUNDARIA, CABECERA_TABLA } from "@/lib/estilos";
import { formatearFechaHora } from "@/lib/formato";
import { interpolar } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { useSinPermisoDeConfigurar } from "@/lib/solo-lectura";
import { cn } from "@/lib/utils";
import { DialogoConfirmacion } from "khipu-design-system/navegacion/dialogo-confirmacion";
import { BotonAsync } from "khipu-design-system/patrones/boton-async";
import { ConfirmacionEnLinea } from "khipu-design-system/patrones/confirmacion-en-linea";
import { Chip, PillEstado } from "khipu-design-system/patrones/pill-estado";
import { CabeceraSeccion } from "khipu-design-system/patrones/cabecera-seccion";

/** Una acción de fila que llama a la API y refresca: el error queda debajo de la fila. La promesa se resuelve al terminar, para que
 * la confirmación del DS (`ConfirmacionEnLinea`, `DialogoConfirmacion`) muestre su estado pendiente mientras tanto. */
function useAccion() {
  const mensajeError = useMensajeError();
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [pendiente, startTransition] = useTransition();
  function ejecutar<T>(ruta: string, init: { method: string; body?: unknown }, alTerminar?: (datos: T | null) => string | null) {
    setError(null);
    setAviso(null);
    return new Promise<void>((resolver) => {
      startTransition(async () => {
        const res = await apiRequest<T>(ruta, init);
        if (res.estado !== "exito") {
          setError(mensajeError(res.codigo, res.mensaje));
        } else {
          setAviso(alTerminar?.(res.datos) ?? null);
          router.refresh();
        }
        resolver();
      });
    });
  }
  return { error, aviso, pendiente, ejecutar };
}

function EstadoDelMiembro({ m }: { m: MiembroDelEquipo }) {
  const t = useMessages().equipo;
  if (!m.activo) return <PillEstado tono="neutro">{t.desactivado}</PillEstado>;
  if (!m.correo_verificado) return <PillEstado tono="aviso">{t.sinVerificar}</PillEstado>;
  return <PillEstado tono="ok">{t.activo}</PillEstado>;
}

function FilaMiembro({ m }: { m: MiembroDelEquipo }) {
  const messages = useMessages();
  const t = messages.equipo;
  const etiquetas = messages.roles.etiquetas;
  const sinPermiso = useSinPermisoDeConfigurar();
  const { error, pendiente, ejecutar } = useAccion();
  // El aviso de API keys acompaña a la confirmación de «Desactivar», que el DS abre y cierra por dentro: se sigue con los clics.
  const [avisoApiKeys, setAvisoApiKeys] = useState(false);
  // H9: el rol al que un administrador quiere bajarse a sí mismo, mientras confirma que pierde el acceso de administrador.
  const [degradandome, setDegradandome] = useState<Rol | null>(null);
  const bloqueado = sinPermiso !== null || pendiente;

  function cambiarRol(rol: Rol) {
    if (m.es_usted && m.rol === "ADMIN" && rol !== "ADMIN") {
      setDegradandome(rol);
      return;
    }
    ejecutar(`/api/proxy/cuenta/usuarios/${m.id}/rol`, { method: "PUT", body: { rol } });
  }

  return (
    <TableRow className={cn(!m.activo && "opacity-80")} data-testid={`miembro-${m.email}`}>
      <DialogoConfirmacion
        open={degradandome !== null}
        onOpenChange={(v) => (v ? null : setDegradandome(null))}
        titulo={t.degradarme.titulo}
        descripcion={t.degradarme.descripcion.replace("{rol}", degradandome ? etiquetas[degradandome] : "")}
        textoConfirmar={t.degradarme.confirmar}
        onConfirmar={() => (degradandome ? ejecutar(`/api/proxy/cuenta/usuarios/${m.id}/rol`, { method: "PUT", body: { rol: degradandome } }) : undefined)}
      />
      <TableCell className="py-2 pr-3 pl-4">
        <div className="flex items-center gap-2">
          <span className="font-mono text-control text-foreground">{m.email}</span>
          {m.es_usted ? <Chip>{t.usted}</Chip> : null}
        </div>
      </TableCell>
      <TableCell className="px-3 py-2">
        <div className="w-40" title={sinPermiso ?? undefined}>
          <SelectCampo
            id={`rol-${m.id}`}
            etiqueta={t.cambiarRol.replace("{email}", m.email)}
            opciones={ROLES.map((r) => ({ value: r, label: etiquetas[r] }))}
            valor={m.rol}
            disabled={bloqueado}
            onCambio={(v) => cambiarRol(v as Rol)}
          />
        </div>
      </TableCell>
      <TableCell className="px-3 py-2 whitespace-nowrap">
        <EstadoDelMiembro m={m} />
      </TableCell>
      <TableCell className="py-2 pr-4 pl-2 text-right">
        <div className="flex flex-col items-end gap-1">
          {m.es_usted ? (
            <span className="text-etiqueta text-muted-foreground/60">—</span>
          ) : !m.activo ? (
            <BotonAsync
              pendiente={pendiente}
              icon={<ArrowCounterClockwiseIcon className="size-3.5" />}
              disabled={bloqueado}
              title={sinPermiso ?? undefined}
              onClick={() => ejecutar(`/api/proxy/cuenta/usuarios/${m.id}/reactivar`, { method: "POST" })}
              className={ACCION_SECUNDARIA}
            >
              {t.reactivar}
            </BotonAsync>
          ) : (
            <div
              className="flex flex-col items-end gap-1"
              onClickCapture={(e) => {
                const boton = (e.target as HTMLElement).closest("button");
                if (boton) setAvisoApiKeys(boton.textContent?.trim() === t.desactivar);
              }}
            >
              {/* La confirmación del DS no recibe `disabled`: sin permiso o con otra acción en curso la apaga el `fieldset`. */}
              <fieldset disabled={bloqueado} title={sinPermiso ?? undefined} className="m-0 min-w-0 border-0 p-0">
                <ConfirmacionEnLinea
                  etiqueta={t.desactivar}
                  pregunta={t.confirmarDesactivar}
                  confirmar={t.siDesactivar}
                  onConfirmar={() => ejecutar(`/api/proxy/cuenta/usuarios/${m.id}/desactivar`, { method: "POST" })}
                />
              </fieldset>
              {/* Las API keys son de la empresa, no de quien las creó: desactivarlo no las revoca. Se dice antes de confirmar, con el camino para hacerlo. */}
              {avisoApiKeys && m.rol === "ADMIN" ? (
                <p className="max-w-xs text-right text-etiqueta text-muted-foreground" data-testid="aviso-api-keys">
                  {t.avisoApiKeys}{" "}
                  <Link href="/api-keys" className="text-primary hover:underline">
                    {t.irAApiKeys}
                  </Link>
                </p>
              ) : null}
            </div>
          )}
          {error ? <span role="alert" className="text-etiqueta text-destructive">{error}</span> : null}
        </div>
      </TableCell>
    </TableRow>
  );
}

function FilaInvitacion({ i }: { i: InvitacionAlEquipo }) {
  const messages = useMessages();
  const locale = useLocale();
  const t = messages.equipo;
  const etiquetas = messages.roles.etiquetas;
  const sinPermiso = useSinPermisoDeConfigurar();
  const { error, aviso, pendiente, ejecutar } = useAccion();
  const bloqueado = sinPermiso !== null || pendiente;

  return (
    <TableRow data-testid={`invitacion-${i.email}`}>
      <TableCell className="py-2 pr-3 pl-4 font-mono text-control text-foreground">{i.email}</TableCell>
      <TableCell className="px-3 py-2 text-control">{etiquetas[i.rol] ?? i.rol}</TableCell>
      <TableCell className="px-3 py-2 whitespace-nowrap">
        {i.vencida ? (
          <PillEstado tono="aviso">{t.vencida}</PillEstado>
        ) : (
          <span className="font-mono text-control text-muted-foreground tabular-nums">{t.venceEl.replace("{fecha}", formatearFechaHora(i.expira_en, locale))}</span>
        )}
        <div className="font-mono text-etiqueta text-muted-foreground/80">{t.envios.replace("{n}", String(i.envios))}</div>
      </TableCell>
      <TableCell className="py-2 pr-4 pl-2 text-right">
        <div className="flex flex-col items-end gap-1">
          <div className="flex items-start gap-1">
            <BotonAsync
              pendiente={pendiente}
              icon={<PaperPlaneTiltIcon className="size-3.5" />}
              disabled={bloqueado}
              title={sinPermiso ?? undefined}
              onClick={() =>
                ejecutar<InvitacionEnviada>(`/api/proxy/cuenta/invitaciones/${i.id}/reenviar`, { method: "POST" }, (d) =>
                  d?.correo_enviado ? t.reenviada.replace("{email}", i.email) : t.reenviadaSinCorreo,
                )
              }
              className={ACCION_SECUNDARIA}
            >
              {t.reenviar}
            </BotonAsync>
            <fieldset disabled={bloqueado} title={sinPermiso ?? undefined} className="m-0 min-w-0 border-0 p-0">
              <ConfirmacionEnLinea etiqueta={t.revocar} pregunta={t.confirmarRevocar} confirmar={t.siRevocar} onConfirmar={() => ejecutar(`/api/proxy/cuenta/invitaciones/${i.id}`, { method: "DELETE" })} />
            </fieldset>
          </div>
          {error ? <span role="alert" className="text-etiqueta text-destructive">{error}</span> : null}
          {aviso ? <span role="status" className="text-etiqueta text-muted-foreground">{aviso}</span> : null}
        </div>
      </TableCell>
    </TableRow>
  );
}

/** #21: los usuarios de la cuenta con su rol, y las invitaciones pendientes. */
export function EquipoView({ miembros, invitaciones }: { miembros: MiembroDelEquipo[]; invitaciones: InvitacionAlEquipo[] }) {
  const t = useMessages().equipo;
  const activos = miembros.filter((m) => m.activo).length;
  return (
    <div className="grid min-w-0 grid-cols-1 gap-6">
      <section className="grid min-w-0 grid-cols-1 gap-3" aria-label={t.usuarios}>
        <CabeceraSeccion icon={UsersIcon} titulo={t.usuarios} subtitulo={interpolar(t.resumenUsuarios, { activos, total: miembros.length })} conBorde={false} className="p-0" />
        <div className={CONTENEDOR_TABLA}>
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead className={cn(CABECERA_TABLA, "pl-4")}>{t.columnas.usuario}</TableHead>
                <TableHead className={CABECERA_TABLA}>{t.columnas.rol}</TableHead>
                <TableHead className={CABECERA_TABLA}>{t.columnas.estado}</TableHead>
                <TableHead className={cn(CABECERA_TABLA, "pr-4 pl-2 text-right")}>{t.columnas.acciones}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody className="text-control">
              {miembros.map((m) => (
                <FilaMiembro key={m.id} m={m} />
              ))}
            </TableBody>
          </Table>
        </div>
      </section>

      <section className="grid min-w-0 grid-cols-1 gap-3" aria-label={t.invitaciones}>
        <CabeceraSeccion icon={EnvelopeSimpleIcon} titulo={t.invitaciones} subtitulo={interpolar(t.pendientes, { n: invitaciones.length })} conBorde={false} className="p-0" />
        <div className={CONTENEDOR_TABLA}>
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead className={cn(CABECERA_TABLA, "pl-4")}>{t.columnas.correo}</TableHead>
                <TableHead className={CABECERA_TABLA}>{t.columnas.rol}</TableHead>
                <TableHead className={CABECERA_TABLA}>{t.columnas.estado}</TableHead>
                <TableHead className={cn(CABECERA_TABLA, "pr-4 pl-2 text-right")}>{t.columnas.acciones}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody className="text-control">
              {invitaciones.map((i) => (
                <FilaInvitacion key={i.id} i={i} />
              ))}
              {invitaciones.length === 0 ? (
                <FilaVacia columnas={4}>{t.sinInvitaciones}</FilaVacia>
              ) : null}
            </TableBody>
          </Table>
        </div>
      </section>
    </div>
  );
}
