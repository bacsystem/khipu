"use client";

import { BanIcon, RefreshCwIcon, StoreIcon } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { apiRequest } from "@/lib/api/browser";
import type { Establecimiento } from "@/lib/api/establecimientos";
import type { Serie } from "@/lib/api/series";
import { mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";
import { EstablecimientoDialog } from "./establecimiento-dialog";

const CONTROL = "h-9 rounded-lg border border-border bg-card text-[12px] font-medium text-foreground shadow-2xs";
const CABECERA = "h-auto px-3 py-2 text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase";
const ACCION =
  "inline-flex h-7 items-center gap-1 rounded-md px-2 text-[12px] font-medium whitespace-nowrap transition-colors disabled:cursor-not-allowed disabled:opacity-60";

function Estado({ activo, principal }: { activo: boolean; principal: boolean }) {
  if (principal)
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-secondary px-2.5 py-0.5 font-mono text-[11px] font-medium text-primary">
        <span className="size-1.5 rounded-full bg-primary" />
        Domicilio fiscal
      </span>
    );
  return activo ? (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-success-border bg-success px-2.5 py-0.5 font-mono text-[11px] font-medium text-success-foreground">
      <span className="size-1.5 rounded-full bg-success-solid" />
      Activo
    </span>
  ) : (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-secondary px-2.5 py-0.5 font-mono text-[11px] font-medium text-muted-foreground">
      <span className="size-1.5 rounded-full bg-muted-foreground/60" />
      Dado de baja
    </span>
  );
}

/** Editar (diálogo) y dar de baja (confirmación en línea). El 0000 se edita en Empresa y no se da de baja. */
function Acciones({ establecimiento, seriesActivas }: { establecimiento: Establecimiento; seriesActivas: string[] }) {
  const router = useRouter();
  const [confirmando, setConfirmando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [bajando, startTransition] = useTransition();

  if (establecimiento.principal)
    return (
      <Link href="/empresa" className={cn(ACCION, "text-primary hover:bg-accent")}>
        Editar en Empresa
      </Link>
    );
  if (!establecimiento.activo) return <span className="text-[11px] text-muted-foreground/60">—</span>;

  function darDeBaja() {
    setError(null);
    startTransition(async () => {
      const res = await apiRequest<null>(`/api/proxy/empresa/establecimientos/${establecimiento.codigo}`, { method: "DELETE" });
      if (res.estado !== "exito") {
        setError(res.mensaje ?? mensajeError(res.codigo));
        return;
      }
      setConfirmando(false);
      router.refresh();
    });
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <div className="flex items-center justify-end gap-1">
        {confirmando ? (
          <>
            <span className="mr-1 text-[11px] text-muted-foreground">¿Dar de baja {establecimiento.codigo}?</span>
            <button type="button" disabled={bajando} onClick={darDeBaja} className={cn(ACCION, "bg-destructive text-destructive-foreground shadow-xs hover:bg-destructive/90")}>
              {bajando ? <RefreshCwIcon className="size-3.5 animate-spin" /> : <BanIcon className="size-3.5" />}
              Sí, dar de baja
            </button>
            <button type="button" disabled={bajando} onClick={() => setConfirmando(false)} className={cn(ACCION, "border border-border bg-card text-foreground/80 hover:bg-muted")}>
              Cancelar
            </button>
          </>
        ) : (
          <>
            <EstablecimientoDialog existente={establecimiento} className={cn(ACCION, "text-primary hover:bg-accent")} />
            <span className="text-border">|</span>
            <button
              type="button"
              onClick={() => setConfirmando(true)}
              disabled={seriesActivas.length > 0}
              title={seriesActivas.length > 0 ? `Tiene series activas (${seriesActivas.join(", ")}): reasígnalas antes de darlo de baja` : undefined}
              className={cn(ACCION, "text-destructive hover:bg-destructive/10")}
              data-testid={`baja-establecimiento-${establecimiento.codigo}`}
            >
              <BanIcon className="size-3.5" />
              Dar de baja
            </button>
          </>
        )}
      </div>
      {error ? (
        <span className="text-[11px] text-destructive" role="alert">
          {error}
        </span>
      ) : null}
    </div>
  );
}

export function EstablecimientosTable({ establecimientos, series }: { establecimientos: Establecimiento[]; series: Serie[] }) {
  const router = useRouter();
  const [refrescando, startTransition] = useTransition();
  const seriesDe = (codigo: string) => series.filter((s) => s.activa && (s.establecimiento ?? "0000") === codigo).map((s) => s.serie);

  return (
    <div className="grid min-w-0 grid-cols-1 gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2.5">
          <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
            <StoreIcon className="size-4" />
          </div>
          <div className="min-w-0">
            <h2 className="text-[13px] font-semibold text-foreground">Puntos de emisión</h2>
            <p className="truncate font-mono text-[11px] text-muted-foreground">Cada serie emite desde uno · su dirección va en el XML (AddressTypeCode, regla 3030)</p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2.5">
          <button
            type="button"
            onClick={() => startTransition(() => router.refresh())}
            title="Refrescar lista"
            className={cn(CONTROL, "inline-flex size-9 items-center justify-center text-muted-foreground transition-colors hover:text-foreground")}
          >
            <RefreshCwIcon className={cn("size-4", refrescando && "animate-spin")} />
          </button>
          <EstablecimientoDialog />
        </div>
      </div>

      <div className={cn("min-w-0 overflow-hidden rounded-xl border border-border/90 bg-card shadow-2xs transition-opacity", refrescando && "opacity-60")}>
        <Table>
          <TableHeader>
            <TableRow className="border-b border-border/80 bg-muted hover:bg-muted">
              <TableHead className={cn(CABECERA, "pl-4")}>Código</TableHead>
              <TableHead className={CABECERA}>Nombre</TableHead>
              <TableHead className={CABECERA}>Dirección</TableHead>
              <TableHead className={CABECERA}>Ubigeo</TableHead>
              <TableHead className={CABECERA}>Series</TableHead>
              <TableHead className={cn(CABECERA, "px-4")}>Estado</TableHead>
              <TableHead className={cn(CABECERA, "pr-4 pl-2 text-right")}>Acciones</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-[13px]">
            {establecimientos.map((e) => {
              const activas = seriesDe(e.codigo);
              return (
                <TableRow key={e.codigo} className={cn("border-b border-border/60 hover:bg-muted/80", !e.activo && "opacity-70")} data-testid={`establecimiento-${e.codigo}`}>
                  <TableCell className="py-2 pr-3 pl-4">
                    <span className={cn("rounded px-2 py-0.5 font-mono text-[13px] font-semibold tracking-tight", e.activo ? "bg-secondary text-primary" : "bg-muted text-muted-foreground")}>{e.codigo}</span>
                  </TableCell>
                  <TableCell className="px-3 py-2 font-medium text-foreground">{e.nombre}</TableCell>
                  <TableCell className="px-3 py-2 text-foreground/90">
                    {e.domicilio.direccion}
                    {e.domicilio.urbanizacion ? <span className="text-muted-foreground">, {e.domicilio.urbanizacion}</span> : null}
                    <div className="text-[11px] text-muted-foreground">
                      {[e.domicilio.distrito, e.domicilio.provincia, e.domicilio.departamento].filter(Boolean).join(" · ")}
                    </div>
                  </TableCell>
                  <TableCell className="px-3 py-2 font-mono text-[12px] text-muted-foreground tabular-nums">{e.domicilio.ubigeo}</TableCell>
                  <TableCell className="px-3 py-2 font-mono text-[12px] text-foreground/90">{activas.length > 0 ? activas.join(", ") : <span className="text-muted-foreground/60">—</span>}</TableCell>
                  <TableCell className="px-4 py-2 whitespace-nowrap">
                    <Estado activo={e.activo} principal={e.principal} />
                  </TableCell>
                  <TableCell className="py-2 pr-4 pl-2 text-right">
                    <Acciones establecimiento={e} seriesActivas={activas} />
                  </TableCell>
                </TableRow>
              );
            })}
            {establecimientos.length === 0 ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={7} className="py-14 text-center">
                  <div className="flex flex-col items-center gap-2 text-muted-foreground">
                    <StoreIcon className="size-6" />
                    <p className="text-sm">
                      Configura el domicilio fiscal en{" "}
                      <Link href="/empresa" className="text-primary hover:underline">
                        Empresa
                      </Link>{" "}
                      y registra tus anexos con «Nuevo establecimiento».
                    </p>
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
