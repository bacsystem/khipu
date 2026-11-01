"use client";

import { SearchIcon, Trash2Icon, UsersIcon } from "lucide-react";
import { useMessages } from "next-intl";
import { useCallback, useEffect, useRef, useState } from "react";
import { PieTabla } from "khipu-design-system/patrones/pie-tabla";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import { apiRequest } from "@/lib/api/browser";
import type { ClienteGuardado } from "@/lib/api/clientes";
import { buscarClientes } from "@/lib/api/clientes-browser";
import { documentosDeCliente } from "@/lib/comprobantes/documentos";
import { CABECERA_TABLA, CONTROL_FILTRO } from "@/lib/estilos";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { POR_PAGINA_DEFECTO } from "khipu-design-system/lib/paginacion";
import { useSoloLectura } from "@/lib/solo-lectura";
import { cn } from "@/lib/utils";
import { ClienteDialog } from "./cliente-dialog";
import { alCambiarLosClientes, avisarQueCambiaronLosClientes } from "./eventos";

const ACCION = "inline-flex h-control items-center gap-1 rounded-lg px-2.5 text-[12px] font-medium whitespace-nowrap transition-colors disabled:cursor-not-allowed disabled:opacity-60";

/** La lista de clientes guardados (#319): búsqueda por documento o nombre, páginas y las acciones de cambiar y eliminar. */
export function ClientesTable() {
  const messages = useMessages();
  const t = messages.clientes.tabla;
  const ETIQUETA_DOCUMENTO = new Map<string, string>(documentosDeCliente(messages).map((d) => [d.codigo, d.etiqueta.split(" — ")[0]]));
  const mensajeError = useMensajeError();
  const soloLectura = useSoloLectura();
  const [texto, setTexto] = useState("");
  const [q, setQ] = useState("");
  const [pagina, setPagina] = useState(1);
  const [porPagina, setPorPagina] = useState<number>(POR_PAGINA_DEFECTO);
  const [version, setVersion] = useState(0);
  const [clientes, setClientes] = useState<ClienteGuardado[] | null>(null);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [confirmando, setConfirmando] = useState<string | null>(null);
  const [eliminando, setEliminando] = useState<string | null>(null);
  const ultimaLectura = useRef(0);

  // Lo tecleado pasa a la búsqueda 300 ms después de la última tecla.
  useEffect(() => {
    const id = window.setTimeout(() => {
      setQ(texto);
      setPagina(1);
    }, 300);
    return () => window.clearTimeout(id);
  }, [texto]);

  useEffect(() => alCambiarLosClientes(() => setVersion((n) => n + 1)), []);

  const cargar = useCallback(async () => {
    const mia = ++ultimaLectura.current;
    const res = await buscarClientes({ q, pagina, porPagina }, messages);
    if (mia !== ultimaLectura.current) return;
    if (res.estado === "exito") {
      // Borrar la única fila de la última página deja esa página vacía: se vuelve a la última que tiene clientes.
      const ultima = Math.max(1, Math.ceil(res.datos.total / porPagina));
      if (pagina > ultima) {
        setPagina(ultima);
        return;
      }
      setClientes(res.datos.clientes);
      setTotal(res.datos.total);
      setError(null);
    } else {
      setError(res.mensaje);
    }
  }, [q, pagina, porPagina, messages]);

  useEffect(() => {
    void cargar();
  }, [cargar, version]);

  async function eliminar(c: ClienteGuardado) {
    setEliminando(c.id);
    const res = await apiRequest<null>(`/api/proxy/clientes/${c.id}`, { method: "DELETE" });
    setEliminando(null);
    setConfirmando(null);
    if (res.estado !== "exito") {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    avisarQueCambiaronLosClientes();
  }

  const ultimaPagina = Math.max(1, Math.ceil(total / porPagina));
  const paginaActual = Math.min(pagina, ultimaPagina);
  const desde = clientes && clientes.length > 0 ? (paginaActual - 1) * porPagina + 1 : 0;
  const hasta = (paginaActual - 1) * porPagina + (clientes?.length ?? 0);

  return (
    <div className="grid min-w-0 grid-cols-1 gap-4" data-testid="clientes">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2.5">
          <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
            <UsersIcon className="size-4" />
          </div>
          <div className="min-w-0">
            <h2 className="text-[13px] font-semibold text-foreground">{t.titulo}</h2>
            <p className="truncate font-mono text-[11px] text-muted-foreground">{t.subtitulo}</p>
          </div>
        </div>
        <label className="relative">
          <span className="sr-only">{t.buscar}</span>
          <SearchIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground/70" />
          <input
            type="search"
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            placeholder={t.buscarPlaceholder}
            className={cn(CONTROL_FILTRO, "w-72 max-w-full pr-3 pl-9 font-normal")}
          />
        </label>
      </div>

      {error ? (
        <p role="alert" className="text-[12px] text-destructive">
          {error}
        </p>
      ) : null}

      <div className="min-w-0 overflow-hidden rounded-xl border border-border/90 bg-card shadow-2xs">
        <Table>
          <TableHeader>
            <TableRow className="border-b border-border/80 bg-muted hover:bg-muted">
              <TableHead className={cn(CABECERA_TABLA, "pl-4")}>{t.columnas.cliente}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.documento}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.direccion}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.correo}</TableHead>
              <TableHead className={cn(CABECERA_TABLA, "pr-4 text-right")}>{t.columnas.acciones}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-[13px]">
            {(clientes ?? []).map((c) => (
              <TableRow key={c.id} data-testid="cliente" className="border-b border-border/60 hover:bg-muted/80">
                <TableCell className="max-w-72 py-2 pr-3 pl-4 font-medium break-words">{c.razon_social}</TableCell>
                <TableCell className="px-3 py-2 whitespace-nowrap">
                  <span className="font-mono tabular-nums">{c.num_doc}</span>
                  <span className="ml-1.5 text-[11px] text-muted-foreground">{ETIQUETA_DOCUMENTO.get(c.tipo_doc) ?? c.tipo_doc}</span>
                </TableCell>
                <TableCell className="max-w-64 px-3 py-2 text-muted-foreground">{c.direccion ?? "—"}</TableCell>
                <TableCell className="px-3 py-2 text-muted-foreground">{c.email ?? "—"}</TableCell>
                <TableCell className="py-2 pr-4 pl-2 text-right">
                  <div className="flex items-center justify-end gap-1">
                    {confirmando === c.id ? (
                      <>
                        <span className="mr-1 text-[11px] text-muted-foreground">{t.confirmarEliminar}</span>
                        <button type="button" disabled={eliminando === c.id} onClick={() => eliminar(c)} className={cn(ACCION, "bg-destructive text-destructive-foreground shadow-xs hover:bg-destructive/90")}>
                          {t.siEliminar}
                        </button>
                        <button type="button" onClick={() => setConfirmando(null)} className={cn(ACCION, "border border-border bg-card text-foreground/80 hover:bg-muted")}>
                          {messages.comun.cancelar}
                        </button>
                      </>
                    ) : (
                      <>
                        <ClienteDialog existente={c} className={cn(ACCION, "border border-border bg-card text-foreground/80 shadow-2xs hover:bg-muted")} />
                        <button type="button" disabled={soloLectura !== null} title={soloLectura ?? undefined} onClick={() => setConfirmando(c.id)} className={cn(ACCION, "text-destructive hover:bg-destructive/10")}>
                          <Trash2Icon className="size-3.5" />
                          {t.eliminar}
                        </button>
                      </>
                    )}
                  </div>
                </TableCell>
              </TableRow>
            ))}
            {clientes !== null && clientes.length === 0 ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={5} className="py-14 text-center">
                  <div className="flex flex-col items-center gap-2 text-muted-foreground">
                    <UsersIcon className="size-6" />
                    <p className="text-sm">{q ? t.sinCoincidencias : t.vacio}</p>
                  </div>
                </TableCell>
              </TableRow>
            ) : null}
            {clientes === null && !error ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={5} className="py-14 text-center text-sm text-muted-foreground">
                  {t.cargando}
                </TableCell>
              </TableRow>
            ) : null}
          </TableBody>
        </Table>
        <PieTabla
          desde={desde}
          hasta={hasta}
          total={total}
          unidad={t.unidad}
          porPagina={porPagina}
          onPorPagina={(n) => {
            setPorPagina(n);
            setPagina(1);
          }}
          nota={t.nota}
          pagina={paginaActual}
          ultimaPagina={ultimaPagina}
          onPagina={setPagina}
        />
      </div>
    </div>
  );
}
