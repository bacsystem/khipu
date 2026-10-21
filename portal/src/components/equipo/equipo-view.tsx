"use client";

import { BanIcon, ChevronDownIcon, MailIcon, RefreshCwIcon, RotateCcwIcon, SendIcon, UsersIcon } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "khipu-design-system/ui/dialog";
import { useState, useTransition } from "react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import { apiRequest } from "@/lib/api/browser";
import { ROLES, type InvitacionAlEquipo, type InvitacionEnviada, type MiembroDelEquipo, type Rol } from "@/lib/api/equipo";
import { BOTON_DESTRUCTIVO, BOTON_SECUNDARIO, CABECERA_TABLA, CONTROL_FILTRO } from "@/lib/estilos";
import { formatearFechaHora } from "@/lib/formato";
import { mensajeError, messages } from "@/lib/messages";
import { useSinPermisoDeConfigurar } from "@/lib/solo-lectura";
import { cn } from "@/lib/utils";

const t = messages.equipo;
const etiquetas = messages.roles.etiquetas;

const ACCION =
  "inline-flex h-9 items-center gap-1 rounded-lg px-2.5 text-[12px] font-medium whitespace-nowrap transition-colors disabled:cursor-not-allowed disabled:opacity-60";
const PASTILLA = "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 font-mono text-[11px] font-medium";

/** Una acción de fila que llama a la API y refresca: el error queda debajo de la fila. */
function useAccion() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [pendiente, startTransition] = useTransition();
  function ejecutar<T>(ruta: string, init: { method: string; body?: unknown }, alTerminar?: (datos: T | null) => string | null) {
    setError(null);
    setAviso(null);
    startTransition(async () => {
      const res = await apiRequest<T>(ruta, init);
      if (res.estado !== "exito") {
        setError(mensajeError(res.codigo));
        return;
      }
      setAviso(alTerminar?.(res.datos) ?? null);
      router.refresh();
    });
  }
  return { error, aviso, pendiente, ejecutar };
}

function EstadoDelMiembro({ m }: { m: MiembroDelEquipo }) {
  if (!m.activo) return <span className={cn(PASTILLA, "border-border bg-secondary text-muted-foreground")}>{t.desactivado}</span>;
  if (!m.correo_verificado) return <span className={cn(PASTILLA, "border-warning-border bg-warning text-warning-foreground")}>{t.sinVerificar}</span>;
  return (
    <span className={cn(PASTILLA, "border-success-border bg-success text-success-foreground")}>
      <span className="size-1.5 rounded-full bg-success-solid" />
      {t.activo}
    </span>
  );
}

function FilaMiembro({ m }: { m: MiembroDelEquipo }) {
  const sinPermiso = useSinPermisoDeConfigurar();
  const { error, pendiente, ejecutar } = useAccion();
  const [confirmando, setConfirmando] = useState(false);
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
    <TableRow className={cn("border-b border-border/60 hover:bg-muted/80", !m.activo && "opacity-80")} data-testid={`miembro-${m.email}`}>
      <Dialog open={degradandome !== null} onOpenChange={(v) => (v ? null : setDegradandome(null))}>
        <DialogContent className="gap-0 p-0">
          <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
            <DialogTitle className="text-base font-semibold tracking-tight">{t.degradarme.titulo}</DialogTitle>
            <DialogDescription className="text-[13px]">
              {t.degradarme.descripcion.replace("{rol}", degradandome ? etiquetas[degradandome] : "")}
            </DialogDescription>
          </DialogHeader>
          <div className="flex items-center justify-end gap-2 px-5 py-3">
            <button type="button" onClick={() => setDegradandome(null)} className={BOTON_SECUNDARIO}>
              {t.cancelar}
            </button>
            <button
              type="button"
              onClick={() => {
                const rol = degradandome;
                setDegradandome(null);
                if (rol) ejecutar(`/api/proxy/cuenta/usuarios/${m.id}/rol`, { method: "PUT", body: { rol } });
              }}
              className={BOTON_DESTRUCTIVO}
            >
              {t.degradarme.confirmar}
            </button>
          </div>
        </DialogContent>
      </Dialog>
      <TableCell className="py-2 pr-3 pl-4">
        <div className="flex items-center gap-2">
          <span className="font-mono text-[12px] text-foreground">{m.email}</span>
          {m.es_usted ? <span className={cn(PASTILLA, "border-primary/20 bg-accent text-accent-foreground")}>{t.usted}</span> : null}
        </div>
      </TableCell>
      <TableCell className="px-3 py-2">
        <div className="relative w-40">
          <select
            aria-label={t.cambiarRol.replace("{email}", m.email)}
            value={m.rol}
            disabled={bloqueado}
            title={sinPermiso ?? undefined}
            onChange={(e) => cambiarRol(e.target.value as Rol)}
            className={cn(CONTROL_FILTRO, "w-full appearance-none pr-8 pl-3 disabled:cursor-not-allowed disabled:opacity-60")}
          >
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {etiquetas[r]}
              </option>
            ))}
          </select>
          <ChevronDownIcon className="pointer-events-none absolute top-2.5 right-2.5 size-4 text-muted-foreground" />
        </div>
      </TableCell>
      <TableCell className="px-3 py-2 whitespace-nowrap">
        <EstadoDelMiembro m={m} />
      </TableCell>
      <TableCell className="py-2 pr-4 pl-2 text-right">
        <div className="flex flex-col items-end gap-1">
          {m.es_usted ? (
            <span className="text-[11px] text-muted-foreground/60">—</span>
          ) : !m.activo ? (
            <button type="button" disabled={bloqueado} title={sinPermiso ?? undefined} onClick={() => ejecutar(`/api/proxy/cuenta/usuarios/${m.id}/reactivar`, { method: "POST" })}
              className={cn(ACCION, "border border-border bg-card text-foreground/80 hover:bg-muted")}>
              <RotateCcwIcon className="size-3.5" />
              {t.reactivar}
            </button>
          ) : confirmando ? (
            <div className="flex flex-col items-end gap-1">
              <div className="flex items-center gap-1">
                <span className="mr-1 text-[11px] text-muted-foreground">{t.confirmarDesactivar}</span>
                <button type="button" disabled={bloqueado} onClick={() => { setConfirmando(false); ejecutar(`/api/proxy/cuenta/usuarios/${m.id}/desactivar`, { method: "POST" }); }}
                  className={cn(ACCION, "bg-destructive text-destructive-foreground shadow-xs hover:bg-destructive/90")}>
                  <BanIcon className="size-3.5" />
                  {t.siDesactivar}
                </button>
                <button type="button" onClick={() => setConfirmando(false)} className={cn(ACCION, "border border-border bg-card text-foreground/80 hover:bg-muted")}>
                  {t.cancelar}
                </button>
              </div>
              {/* Las API keys son de la empresa, no de quien las creó: desactivarlo no las revoca. Se dice antes de confirmar, con el camino para hacerlo. */}
              {m.rol === "ADMIN" ? (
                <p className="max-w-xs text-right text-[11px] text-muted-foreground" data-testid="aviso-api-keys">
                  {t.avisoApiKeys}{" "}
                  <Link href="/api-keys" className="text-primary hover:underline">
                    {t.irAApiKeys}
                  </Link>
                </p>
              ) : null}
            </div>
          ) : (
            <button type="button" disabled={bloqueado} title={sinPermiso ?? undefined} onClick={() => setConfirmando(true)} className={cn(ACCION, "text-destructive hover:bg-destructive/10")}>
              <BanIcon className="size-3.5" />
              {t.desactivar}
            </button>
          )}
          {error ? <span role="alert" className="text-[11px] text-destructive">{error}</span> : null}
        </div>
      </TableCell>
    </TableRow>
  );
}

function FilaInvitacion({ i }: { i: InvitacionAlEquipo }) {
  const sinPermiso = useSinPermisoDeConfigurar();
  const { error, aviso, pendiente, ejecutar } = useAccion();
  const [confirmando, setConfirmando] = useState(false);
  const bloqueado = sinPermiso !== null || pendiente;

  return (
    <TableRow className="border-b border-border/60 hover:bg-muted/80" data-testid={`invitacion-${i.email}`}>
      <TableCell className="py-2 pr-3 pl-4 font-mono text-[12px] text-foreground">{i.email}</TableCell>
      <TableCell className="px-3 py-2 text-[12px]">{etiquetas[i.rol] ?? i.rol}</TableCell>
      <TableCell className="px-3 py-2 whitespace-nowrap">
        {i.vencida ? (
          <span className={cn(PASTILLA, "border-warning-border bg-warning text-warning-foreground")}>{t.vencida}</span>
        ) : (
          <span className="font-mono text-[12px] text-muted-foreground tabular-nums">{t.venceEl.replace("{fecha}", formatearFechaHora(i.expira_en))}</span>
        )}
        <div className="font-mono text-[11px] text-muted-foreground/80">{t.envios.replace("{n}", String(i.envios))}</div>
      </TableCell>
      <TableCell className="py-2 pr-4 pl-2 text-right">
        <div className="flex flex-col items-end gap-1">
          {confirmando ? (
            <div className="flex items-center gap-1">
              <span className="mr-1 text-[11px] text-muted-foreground">{t.confirmarRevocar}</span>
              <button type="button" disabled={bloqueado} onClick={() => { setConfirmando(false); ejecutar(`/api/proxy/cuenta/invitaciones/${i.id}`, { method: "DELETE" }); }}
                className={cn(ACCION, "bg-destructive text-destructive-foreground shadow-xs hover:bg-destructive/90")}>
                <BanIcon className="size-3.5" />
                {t.siRevocar}
              </button>
              <button type="button" onClick={() => setConfirmando(false)} className={cn(ACCION, "border border-border bg-card text-foreground/80 hover:bg-muted")}>
                {t.cancelar}
              </button>
            </div>
          ) : (
            <div className="flex items-center gap-1">
              <button type="button" disabled={bloqueado} title={sinPermiso ?? undefined}
                onClick={() => ejecutar<InvitacionEnviada>(`/api/proxy/cuenta/invitaciones/${i.id}/reenviar`, { method: "POST" },
                  (d) => (d?.correo_enviado ? t.reenviada.replace("{email}", i.email) : t.reenviadaSinCorreo))}
                className={cn(ACCION, "border border-border bg-card text-foreground/80 hover:bg-muted")}>
                {pendiente ? <RefreshCwIcon className="size-3.5 animate-spin" /> : <SendIcon className="size-3.5" />}
                {t.reenviar}
              </button>
              <button type="button" disabled={bloqueado} title={sinPermiso ?? undefined} onClick={() => setConfirmando(true)} className={cn(ACCION, "text-destructive hover:bg-destructive/10")}>
                <BanIcon className="size-3.5" />
                {t.revocar}
              </button>
            </div>
          )}
          {error ? <span role="alert" className="text-[11px] text-destructive">{error}</span> : null}
          {aviso ? <span role="status" className="text-[11px] text-muted-foreground">{aviso}</span> : null}
        </div>
      </TableCell>
    </TableRow>
  );
}

function Encabezado({ icono: Icono, titulo, detalle }: { icono: typeof UsersIcon; titulo: string; detalle: string }) {
  return (
    <div className="flex min-w-0 items-center gap-2.5">
      <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
        <Icono className="size-4" />
      </div>
      <div className="min-w-0">
        <h2 className="text-[13px] font-semibold text-foreground">{titulo}</h2>
        <p className="truncate font-mono text-[11px] text-muted-foreground">{detalle}</p>
      </div>
    </div>
  );
}

/** #21: los usuarios de la cuenta con su rol, y las invitaciones pendientes. */
export function EquipoView({ miembros, invitaciones }: { miembros: MiembroDelEquipo[]; invitaciones: InvitacionAlEquipo[] }) {
  const activos = miembros.filter((m) => m.activo).length;
  return (
    <div className="grid min-w-0 grid-cols-1 gap-6">
      <section className="grid min-w-0 grid-cols-1 gap-3" aria-label={t.usuarios}>
        <Encabezado icono={UsersIcon} titulo={t.usuarios} detalle={`${activos} activos · ${miembros.length} en total`} />
        <div className="min-w-0 overflow-x-auto rounded-xl border border-border/90 bg-card shadow-2xs">
          <Table>
            <TableHeader>
              <TableRow className="border-b border-border/80 bg-muted hover:bg-muted">
                <TableHead className={cn(CABECERA_TABLA, "pl-4")}>{t.columnas.usuario}</TableHead>
                <TableHead className={CABECERA_TABLA}>{t.columnas.rol}</TableHead>
                <TableHead className={CABECERA_TABLA}>{t.columnas.estado}</TableHead>
                <TableHead className={cn(CABECERA_TABLA, "pr-4 pl-2 text-right")}>{t.columnas.acciones}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody className="text-[13px]">
              {miembros.map((m) => (
                <FilaMiembro key={m.id} m={m} />
              ))}
            </TableBody>
          </Table>
        </div>
      </section>

      <section className="grid min-w-0 grid-cols-1 gap-3" aria-label={t.invitaciones}>
        <Encabezado icono={MailIcon} titulo={t.invitaciones} detalle={`${invitaciones.length} pendientes`} />
        <div className="min-w-0 overflow-x-auto rounded-xl border border-border/90 bg-card shadow-2xs">
          <Table>
            <TableHeader>
              <TableRow className="border-b border-border/80 bg-muted hover:bg-muted">
                <TableHead className={cn(CABECERA_TABLA, "pl-4")}>{t.columnas.correo}</TableHead>
                <TableHead className={CABECERA_TABLA}>{t.columnas.rol}</TableHead>
                <TableHead className={CABECERA_TABLA}>{t.columnas.estado}</TableHead>
                <TableHead className={cn(CABECERA_TABLA, "pr-4 pl-2 text-right")}>{t.columnas.acciones}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody className="text-[13px]">
              {invitaciones.map((i) => (
                <FilaInvitacion key={i.id} i={i} />
              ))}
              {invitaciones.length === 0 ? (
                <TableRow className="hover:bg-transparent">
                  <TableCell colSpan={4} className="py-10 text-center text-sm text-muted-foreground">
                    {t.sinInvitaciones}
                  </TableCell>
                </TableRow>
              ) : null}
            </TableBody>
          </Table>
        </div>
      </section>
    </div>
  );
}
