"use client";

import { ArrowClockwiseIcon, KeyIcon } from "@phosphor-icons/react";
import { useRouter } from "next/navigation";
import { useLocale, useMessages } from "next-intl";
import { useMemo, useState, useTransition } from "react";
import { BotonCopiar } from "khipu-design-system/patrones/boton-copiar";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "khipu-design-system/ui/select";
import { CONTENEDOR_TABLA, FilaVacia, PieTabla } from "khipu-design-system/patrones/pie-tabla";
import { PillEstado } from "khipu-design-system/patrones/pill-estado";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import type { ApiKeyResumen } from "@/lib/api/api-keys";
import { apiRequest } from "@/lib/api/browser";
import { formatearFechaHora } from "@/lib/formato";
import { interpolar } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { POR_PAGINA_DEFECTO } from "khipu-design-system/lib/paginacion";
import { cn } from "@/lib/utils";
import { CABECERA_TABLA, CONTROL_FILTRO } from "@/lib/estilos";
import { useSinPermisoDeConfigurar } from "@/lib/solo-lectura";
import { CabeceraSeccion } from "khipu-design-system/patrones/cabecera-seccion";
import { ConfirmacionEnLinea } from "khipu-design-system/patrones/confirmacion-en-linea";
import { Spinner } from "khipu-design-system/feedback/spinner";

const TODAS = "todas";

function Estado({ activa }: { activa: boolean }) {
  const t = useMessages().apiKeys.tabla;
  return activa ? <PillEstado tono="ok">{t.activa}</PillEstado> : <PillEstado tono="neutro">{t.revocada}</PillEstado>;
}

/** Acciones de una fila: "Revocar" pide confirmación en línea antes de llamar a la API (es irreversible). */
function Acciones({ apiKey }: { apiKey: ApiKeyResumen }) {
  const soloLectura = useSinPermisoDeConfigurar();
  const t = useMessages().apiKeys.tabla;
  const mensajeError = useMensajeError();
  const router = useRouter();

  if (!apiKey.activa) return <span className="text-etiqueta text-muted-foreground/60">—</span>;

  async function revocar() {
    const res = await apiRequest<null>(`/api/proxy/empresa/api-keys/${apiKey.id}`, { method: "DELETE" });
    if (res.estado !== "exito") return mensajeError(res.codigo);
    router.refresh();
  }

  // Sin «Regenerar» deshabilitado (C6): para rotar una llave se crea otra con «Crear API key» y se revoca esta.
  // La confirmación del DS no recibe `disabled`: sin permiso la apaga el `fieldset`, que lleva el título.
  return (
    <fieldset disabled={soloLectura !== null} title={soloLectura ?? undefined} className="m-0 min-w-0 border-0 p-0">
      <ConfirmacionEnLinea etiqueta={t.revocar} pregunta={t.confirmarRevocar} confirmar={t.siRevocar} onConfirmar={revocar} />
    </fieldset>
  );
}

export function ApiKeysTable({ apiKeys }: { apiKeys: ApiKeyResumen[] }) {
  const locale = useLocale();
  const messages = useMessages();
  const t = messages.apiKeys.tabla;
  const itemsEstado: Record<string, string> = { [TODAS]: t.filtro.todas, activa: t.filtro.activas, revocada: t.filtro.revocadas };
  const router = useRouter();
  const [refrescando, startTransition] = useTransition();
  const [estado, setEstado] = useState(TODAS);
  const [pagina, setPagina] = useState(1);
  const [porPagina, setPorPagina] = useState<number>(POR_PAGINA_DEFECTO);

  // La API devuelve todas las llaves del tenant (son pocas), así que filtro y pagino en el cliente.
  const filtradas = useMemo(
    () =>
      apiKeys.filter((k) => {
        if (estado === "activa") return k.activa;
        if (estado === "revocada") return !k.activa;
        return true;
      }),
    [apiKeys, estado],
  );

  const total = filtradas.length;
  const ultimaPagina = Math.max(1, Math.ceil(total / porPagina));
  const paginaActual = Math.min(pagina, ultimaPagina);
  const data = filtradas.slice((paginaActual - 1) * porPagina, paginaActual * porPagina);
  const desde = data.length === 0 ? 0 : (paginaActual - 1) * porPagina + 1;
  const hasta = (paginaActual - 1) * porPagina + data.length;

  return (
    <div className="grid min-w-0 grid-cols-1 gap-4">
      <CabeceraSeccion
        icon={KeyIcon}
        titulo={t.titulo}
        subtitulo={t.subtitulo}
        conBorde={false}
        className="p-0"
        derecha={
          <>

            <div className="flex flex-wrap items-center gap-2.5">
              <Select
                items={itemsEstado}
                value={estado}
                onValueChange={(v) => {
                  setEstado(v ?? TODAS);
                  setPagina(1);
                }}
              >
                <SelectTrigger className={cn(CONTROL_FILTRO, "w-auto min-w-36 pl-3")}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {Object.entries(itemsEstado).map(([valor, etiqueta]) => (
                    <SelectItem key={valor} value={valor}>
                      {etiqueta}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>

              <button
                type="button"
                onClick={() => startTransition(() => router.refresh())}
                title={t.refrescar}
                className={cn(CONTROL_FILTRO, "inline-flex size-control items-center justify-center text-muted-foreground transition-colors hover:text-foreground")}
              >
                {refrescando ? <Spinner aria-label={messages.comun.cargando} /> : <ArrowClockwiseIcon weight="bold" className="size-4" />}
              </button>
            </div>
          </>
        }
      />

      <div className={cn(CONTENEDOR_TABLA, refrescando && "opacity-60")}>
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className={cn(CABECERA_TABLA, "pl-4")}>{t.columnas.llave}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.creada}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.revocada}</TableHead>
              <TableHead className={cn(CABECERA_TABLA, "px-4")}>{t.columnas.estado}</TableHead>
              <TableHead className={cn(CABECERA_TABLA, "pr-4 pl-2 text-right")}>{t.columnas.acciones}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-control">
            {data.map((k) => (
              <TableRow key={k.id} className={cn("group", !k.activa && "opacity-80")}>
                <TableCell className="py-2 pr-3 pl-4">
                  <div className="flex items-center gap-1.5">
                    <span
                      className={cn(
                        "rounded px-2 py-0.5 font-mono text-cuerpo font-semibold tracking-tight",
                        k.activa ? "bg-secondary text-primary" : "bg-muted text-muted-foreground line-through",
                      )}
                    >
                      {k.prefijo}
                    </span>
                    <span className="font-mono text-control tracking-[0.2em] text-muted-foreground/50 select-none">••••••••••</span>
                    <BotonCopiar texto={k.prefijo} titulo={t.copiarPrefijo} className="opacity-0 group-hover:opacity-100" />
                  </div>
                  <div className="mt-0.5 font-mono text-etiqueta text-muted-foreground/80" title={k.id}>
                    {interpolar(t.id, { id: k.id.slice(0, 8) })}
                  </div>
                </TableCell>
                <TableCell className="px-3 py-2 font-mono text-control whitespace-nowrap text-foreground tabular-nums">{formatearFechaHora(k.creada_en, locale)}</TableCell>
                <TableCell className="px-3 py-2 font-mono text-control whitespace-nowrap text-muted-foreground tabular-nums">
                  {k.revocada_en ? formatearFechaHora(k.revocada_en, locale) : "—"}
                </TableCell>
                <TableCell className="px-4 py-2 whitespace-nowrap">
                  <Estado activa={k.activa} />
                </TableCell>
                <TableCell className="py-2 pr-4 pl-2 text-right whitespace-normal">
                  <Acciones apiKey={k} />
                </TableCell>
              </TableRow>
            ))}
            {data.length === 0 ? (
              <FilaVacia columnas={5} icon={<KeyIcon className="size-6" />}>
                {apiKeys.length === 0 ? t.vacia : t.sinResultados}
              </FilaVacia>
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
