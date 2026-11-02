"use client";

import { PaperPlaneTiltIcon, PlusIcon, TrashIcon } from "@phosphor-icons/react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMessages } from "next-intl";
import { type FormEvent, type ReactNode, useRef, useState } from "react";
import { Casilla } from "khipu-design-system/formularios/casilla";
import { EntradaFecha } from "khipu-design-system/formularios/entrada-fecha";
import { GrupoOpciones } from "khipu-design-system/formularios/opciones";
import { UbigeoSelector } from "@/components/empresa/ubigeo-selector";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { interpolar, type Mensajes } from "@/i18n/mensajes";
import { apiRequest } from "@/lib/api/browser";
import { noSeSabeSiLlego } from "@/lib/api/corte-de-red";
import type { Establecimiento } from "@/lib/api/establecimientos";
import type { Guia } from "@/lib/api/guias";
import type { Serie } from "@/lib/api/series";
import { AYUDA_CAMPO, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO, SELECT_NATIVO, TARJETA, TITULO_SECCION } from "@/lib/estilos";
import {
  conductorVacio,
  conTiposAplicables,
  cuerpoDeGuia,
  documentoVacio,
  esDeTransportista,
  guiaVacia,
  INDICADORES,
  INDICADORES_DE_TRANSPORTISTA,
  itemVacio,
  llevaVehiculos,
  MAX_CONDUCTORES,
  MAX_DOCUMENTOS,
  MAX_OBSERVACIONES,
  MAX_VEHICULOS,
  validarGuia,
  type ConductorForm,
  type DocumentoForm,
  type GuiaForm,
  type ItemForm,
  type ParteForm,
  type PuntoForm,
} from "@/lib/guias/formulario";
import { CABECERA_IDEMPOTENCIA, intentoPara, type Intento } from "@/lib/idempotencia";
import { useSoloLectura } from "@/lib/solo-lectura";
import { cn } from "@/lib/utils";

/** Una entrada de un catálogo SUNAT, tal como la necesitan los selectores. */
export type OpcionDeCatalogo = { codigo: string; descripcion: string; extra?: Record<string, string> };

export type CatalogosDeGuia = {
  /** Catálogo 20: motivos de traslado. */
  motivos: OpcionDeCatalogo[];
  /** Catálogo 06: tipos de documento de identidad. */
  tiposDocumento: OpcionDeCatalogo[];
  /** Catálogo 61: documentos que sustentan el traslado. */
  documentos: OpcionDeCatalogo[];
};

function Campo({ id, etiqueta, ayuda, children, className }: { id: string; etiqueta: string; ayuda?: string; children: ReactNode; className?: string }) {
  return (
    <div className={cn("flex min-w-0 flex-col gap-1.5", className)}>
      <label htmlFor={id} className={ETIQUETA_CAMPO}>
        {etiqueta}
      </label>
      {children}
      {ayuda ? <span className={AYUDA_CAMPO}>{ayuda}</span> : null}
    </div>
  );
}

function Seccion({ titulo, ayuda, children }: { titulo: string; ayuda?: string; children: ReactNode }) {
  return (
    <section className={cn(TARJETA, "flex flex-col gap-4 p-4")}>
      <div className="flex flex-col gap-1">
        <h2 className={TITULO_SECCION}>{titulo}</h2>
        {ayuda ? <p className={AYUDA_CAMPO}>{ayuda}</p> : null}
      </div>
      {children}
    </section>
  );
}

function Seleccion({ id, valor, alCambiar, opciones, vacio, deshabilitado }: { id: string; valor: string; alCambiar: (v: string) => void; opciones: Array<{ valor: string; texto: string }>; vacio?: string; deshabilitado?: boolean }) {
  return (
    <select id={id} value={valor} disabled={deshabilitado} onChange={(e) => alCambiar(e.target.value)} className={cn(SELECT_NATIVO, "w-full")}>
      {vacio !== undefined ? <option value="">{vacio}</option> : null}
      {opciones.map((o) => (
        <option key={o.valor} value={o.valor}>
          {o.texto}
        </option>
      ))}
    </select>
  );
}

const textoDeOpcion = (o: OpcionDeCatalogo) => `${o.codigo} · ${o.descripcion}`;

/** Quién, con su documento, de una parte de la guía (destinatario, remitente). */
function Parte({ prefijo, parte, alCambiar, tipos }: { prefijo: string; parte: ParteForm; alCambiar: (p: ParteForm) => void; tipos: OpcionDeCatalogo[] }) {
  const t = useMessages().guias.form;
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
      <Campo id={`${prefijo}-tipo`} etiqueta={t.tipoDoc}>
        {tipos.length > 0 ? (
          <Seleccion id={`${prefijo}-tipo`} valor={parte.tipo_doc} alCambiar={(v) => alCambiar({ ...parte, tipo_doc: v })} opciones={tipos.map((o) => ({ valor: o.codigo, texto: textoDeOpcion(o) }))} />
        ) : (
          <input id={`${prefijo}-tipo`} value={parte.tipo_doc} onChange={(e) => alCambiar({ ...parte, tipo_doc: e.target.value })} className={cn(CAMPO, "font-mono")} />
        )}
      </Campo>
      <Campo id={`${prefijo}-num`} etiqueta={t.numDoc}>
        <input id={`${prefijo}-num`} value={parte.num_doc} onChange={(e) => alCambiar({ ...parte, num_doc: e.target.value })} maxLength={15} className={cn(CAMPO, "font-mono")} />
      </Campo>
      <Campo id={`${prefijo}-nombre`} etiqueta={t.nombre}>
        <input id={`${prefijo}-nombre`} value={parte.nombre} onChange={(e) => alCambiar({ ...parte, nombre: e.target.value })} maxLength={250} className={CAMPO} />
      </Campo>
    </div>
  );
}

/** Un punto del traslado: un establecimiento de la empresa (solo en la guía remitente) o una dirección con su ubigeo. */
function Punto({ prefijo, titulo, punto, alCambiar, establecimientos, admiteEstablecimiento }: { prefijo: string; titulo: string; punto: PuntoForm; alCambiar: (p: PuntoForm) => void; establecimientos: Establecimiento[]; admiteEstablecimiento: boolean }) {
  const t = useMessages().guias.form;
  // Solo los establecimientos que la empresa tiene: el `0000` figura únicamente si cargó su domicilio fiscal, y la API rechaza un código que no existe (3366, 3367).
  const opciones = establecimientos.filter((e) => e.activo).map((e) => ({ valor: e.codigo, texto: `${e.codigo} · ${e.principal ? t.domicilioFiscal : e.nombre}` }));
  const conEstablecimientos = admiteEstablecimiento && opciones.length > 0;
  const modo = conEstablecimientos ? punto.modo : "direccion";
  const aEstablecimiento = () => alCambiar({ ...punto, modo: "establecimiento", establecimiento: opciones.some((o) => o.valor === punto.establecimiento) ? punto.establecimiento : opciones[0].valor });
  return (
    <fieldset className="flex flex-col gap-3 rounded-lg border border-border p-3">
      <legend className="px-1 text-control font-semibold text-foreground">{titulo}</legend>
      {conEstablecimientos ? (
        <GrupoOpciones<PuntoForm["modo"]>
          nombre={`${prefijo}-modo`}
          opciones={[
            { valor: "establecimiento", etiqueta: t.puntoEstablecimiento },
            { valor: "direccion", etiqueta: t.puntoDireccion },
          ]}
          valor={modo}
          onCambio={(v) => (v === "establecimiento" ? aEstablecimiento() : alCambiar({ ...punto, modo: "direccion" }))}
          className="flex flex-wrap gap-4"
        />
      ) : null}
      {modo === "establecimiento" ? (
        <Campo id={`${prefijo}-establecimiento`} etiqueta={t.puntoEstablecimientoSelect}>
          <Seleccion
            id={`${prefijo}-establecimiento`}
            valor={punto.establecimiento}
            alCambiar={(v) => alCambiar({ ...punto, establecimiento: v })}
            opciones={opciones}
          />
        </Campo>
      ) : (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <UbigeoSelector value={punto.ubigeo} onChange={(ubigeo) => alCambiar({ ...punto, ubigeo })} idPrefijo={prefijo} />
          </div>
          <Campo id={`${prefijo}-direccion`} etiqueta={t.direccion}>
            <input id={`${prefijo}-direccion`} value={punto.direccion} onChange={(e) => alCambiar({ ...punto, direccion: e.target.value })} maxLength={500} className={CAMPO} />
          </Campo>
        </>
      )}
    </fieldset>
  );
}

/** Los documentos del catálogo 61 que admite cada guía: la columna «GRE aplicable» dice «remitente, transportista», «solo remitente» o «solo transportista». */
function documentosDe(documentos: OpcionDeCatalogo[], transportista: boolean): OpcionDeCatalogo[] {
  return documentos.filter((d) => {
    const aplica = d.extra?.["GRE aplicable"] ?? "";
    return !aplica.includes("solo") || aplica.includes(transportista ? "transportista" : "remitente");
  });
}

function reemplazar<T>(lista: T[], i: number, valor: T): T[] {
  return lista.map((x, n) => (n === i ? valor : x));
}

type Props = {
  /** Las series de guía de la empresa (09 y 31) activas. */
  series: Serie[];
  establecimientos: Establecimiento[];
  catalogos: CatalogosDeGuia;
  hoy: string;
};

/**
 * El formulario de «Nueva guía de remisión» (#39). La letra de la serie decide la guía y con ella qué campos se piden: la remitente, motivo y modalidad (con la empresa
 * de transporte si es pública, o con vehículos y conductores si es privada); la transportista, remitente, registro MTC y vehículo y conductor principales.
 *
 * «Validar» manda el mismo cuerpo a `/validar`, que aplica las reglas de la emisión sin numerar ni guardar nada. La clave de idempotencia sigue la misma regla que en las
 * facturas: la misma mientras se reintente el mismo contenido (un corte de red, un doble clic), otra si el contenido cambió.
 */
export function NuevaGuiaForm({ series, establecimientos, catalogos, hoy }: Props) {
  const messages = useMessages();
  const t = messages.guias.form;
  const mensajeError = useMensajeError();
  const router = useRouter();
  const soloLectura = useSoloLectura();
  const [f, setF] = useState<GuiaForm>(() => guiaVacia(series[0]?.serie ?? "", hoy, establecimientos.some((e) => e.principal && e.activo)));
  const [enviando, setEnviando] = useState<"validar" | "emitir" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [valida, setValida] = useState(false);
  const enCurso = useRef(false);
  const intento = useRef<Intento | null>(null);

  const transportista = esDeTransportista(f.serie);
  const conVehiculos = llevaVehiculos(f);
  const tiposDocumento = catalogos.tiposDocumento;
  const documentosAplicables = documentosDe(catalogos.documentos, transportista);
  const indicadoresOfrecidos = transportista ? INDICADORES_DE_TRANSPORTISTA : INDICADORES.filter((i) => (i === "vehiculo_m1_o_l" ? f.modalidad === "02" : i === "vehiculos_conductores_transportista" ? f.modalidad === "01" : true));

  function cambiar(parcial: Partial<GuiaForm>) {
    setF((previo) => ({ ...previo, ...parcial }));
    setValida(false);
    setError(null);
  }

  async function enviar(accion: "validar" | "emitir") {
    if (enCurso.current) return;
    setError(null);
    setValida(false);
    const faltante = validarGuia(f);
    if (faltante) {
      setError(t.errores[faltante]);
      return;
    }
    const cuerpo = cuerpoDeGuia(f);
    enCurso.current = true;
    setEnviando(accion);
    let res;
    if (accion === "validar") {
      res = await apiRequest<Guia>("/api/proxy/guias-remision/validar", { method: "POST", body: cuerpo });
    } else {
      intento.current = intentoPara(intento.current, JSON.stringify(cuerpo));
      res = await apiRequest<Guia>("/api/proxy/guias-remision", { method: "POST", body: cuerpo, headers: { [CABECERA_IDEMPOTENCIA]: intento.current.clave } });
    }
    enCurso.current = false;
    setEnviando(null);
    if (noSeSabeSiLlego(res)) {
      // Un corte no dice si la guía se numeró: se manda a revisar el listado y el próximo intento reusa la clave, así que no duplica.
      setError(accion === "emitir" ? t.sinConexion : messages.errores.RED);
      return;
    }
    if (res.estado !== "exito" || !res.datos) {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    if (accion === "validar") {
      setValida(true);
      return;
    }
    router.push(`/guias/${res.datos.id}`);
    router.refresh();
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    void enviar("emitir");
  }

  const bloqueado = enviando !== null || soloLectura !== null;

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4" data-testid="nueva-guia-form" noValidate>
      <Seccion titulo={t.titulo}>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Campo id="guia-serie" etiqueta={t.serie} ayuda={t.serieAyuda}>
            <Seleccion
              id="guia-serie"
              valor={f.serie}
              alCambiar={(serie) =>
                cambiar({ serie, documentos: conTiposAplicables(f.documentos, documentosDe(catalogos.documentos, esDeTransportista(serie)).map((d) => d.codigo)) })
              }
              opciones={series.map((s) => ({ valor: s.serie, texto: interpolar(t.serieTipo, { serie: s.serie, tipo: messages.guias.tipos[s.tipo === "31" ? "31" : "09"] }) }))}
            />
          </Campo>
          <Campo id="guia-fecha" etiqueta={t.fechaEmision} ayuda={t.fechaEmisionAyuda}>
            <EntradaFecha id="guia-fecha" valor={f.fecha_emision} max={hoy} onCambio={(fecha_emision) => cambiar({ fecha_emision })} />
          </Campo>
        </div>
      </Seccion>

      <Seccion titulo={t.destinatario}>
        <Parte prefijo="destinatario" parte={f.destinatario} alCambiar={(destinatario) => cambiar({ destinatario })} tipos={tiposDocumento} />
      </Seccion>

      {transportista ? (
        <Seccion titulo={t.remitente}>
          <Parte prefijo="remitente" parte={f.remitente} alCambiar={(remitente) => cambiar({ remitente })} tipos={tiposDocumento} />
          <Campo id="guia-mtc" etiqueta={t.registroMtc} ayuda={t.registroMtcObligatorio}>
            <input id="guia-mtc" value={f.registro_mtc} onChange={(e) => cambiar({ registro_mtc: e.target.value })} maxLength={20} className={cn(CAMPO, "font-mono")} />
          </Campo>
        </Seccion>
      ) : (
        <Seccion titulo={t.motivo}>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Campo id="guia-motivo" etiqueta={t.motivo}>
              {catalogos.motivos.length > 0 ? (
                <Seleccion id="guia-motivo" valor={f.motivo} alCambiar={(motivo) => cambiar({ motivo })} opciones={catalogos.motivos.map((o) => ({ valor: o.codigo, texto: textoDeOpcion(o) }))} vacio={t.motivoPlaceholder} />
              ) : (
                <input id="guia-motivo" value={f.motivo} onChange={(e) => cambiar({ motivo: e.target.value })} maxLength={2} className={cn(CAMPO, "font-mono")} />
              )}
            </Campo>
            <Campo id="guia-motivo-descripcion" etiqueta={t.descripcionMotivo} ayuda={t.descripcionMotivoAyuda}>
              <input id="guia-motivo-descripcion" value={f.descripcion_motivo} onChange={(e) => cambiar({ descripcion_motivo: e.target.value })} maxLength={100} className={CAMPO} />
            </Campo>
          </div>
          <fieldset className="flex flex-col gap-2">
            <legend className={ETIQUETA_CAMPO}>{t.modalidad}</legend>
            <GrupoOpciones<GuiaForm["modalidad"]>
              nombre="guia-modalidad"
              opciones={(["02", "01"] as const).map((m) => ({ valor: m, etiqueta: t.modalidades[m] }))}
              valor={f.modalidad}
              onCambio={(modalidad) => cambiar({ modalidad })}
            />
          </fieldset>
          {f.modalidad === "01" ? (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <Campo id="guia-transportista-ruc" etiqueta={t.transportistaRuc}>
                <input id="guia-transportista-ruc" value={f.transportista.ruc} onChange={(e) => cambiar({ transportista: { ...f.transportista, ruc: e.target.value } })} maxLength={11} className={cn(CAMPO, "font-mono")} />
              </Campo>
              <Campo id="guia-transportista-nombre" etiqueta={t.transportistaNombre}>
                <input id="guia-transportista-nombre" value={f.transportista.razon_social} onChange={(e) => cambiar({ transportista: { ...f.transportista, razon_social: e.target.value } })} maxLength={250} className={CAMPO} />
              </Campo>
              <Campo id="guia-transportista-mtc" etiqueta={t.registroMtcOpcional}>
                <input id="guia-transportista-mtc" value={f.transportista.registro_mtc} onChange={(e) => cambiar({ transportista: { ...f.transportista, registro_mtc: e.target.value } })} maxLength={20} className={cn(CAMPO, "font-mono")} />
              </Campo>
            </div>
          ) : null}
        </Seccion>
      )}

      <Seccion titulo={t.traslado}>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Campo id="guia-inicio" etiqueta={t.fechaInicio} ayuda={t.fechaInicioAyuda}>
            <EntradaFecha id="guia-inicio" valor={f.fecha_inicio} min={f.fecha_emision} onCambio={(fecha_inicio) => cambiar({ fecha_inicio })} />
          </Campo>
          <Campo id="guia-peso" etiqueta={t.pesoBruto}>
            <input id="guia-peso" inputMode="decimal" value={f.peso_bruto} onChange={(e) => cambiar({ peso_bruto: e.target.value })} className={cn(CAMPO, "font-mono")} />
          </Campo>
          <Campo id="guia-unidad-peso" etiqueta={t.unidadPeso}>
            <Seleccion id="guia-unidad-peso" valor={f.unidad_peso} alCambiar={(v) => cambiar({ unidad_peso: v === "TNE" ? "TNE" : "KGM" })} opciones={(["KGM", "TNE"] as const).map((u) => ({ valor: u, texto: `${u} · ${t.unidadesPeso[u]}` }))} />
          </Campo>
        </div>
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <Punto prefijo="partida" titulo={t.partida} punto={f.partida} alCambiar={(partida) => cambiar({ partida })} establecimientos={establecimientos} admiteEstablecimiento={!transportista} />
          <Punto prefijo="llegada" titulo={t.llegada} punto={f.llegada} alCambiar={(llegada) => cambiar({ llegada })} establecimientos={establecimientos} admiteEstablecimiento={!transportista} />
        </div>
        <fieldset className="flex flex-col gap-2">
          <legend className={ETIQUETA_CAMPO}>{t.indicadores}</legend>
          <p className={AYUDA_CAMPO}>{t.indicadoresAyuda}</p>
          {indicadoresOfrecidos.map((i) => (
            <Casilla
              key={i}
              id={`guia-indicador-${i}`}
              etiqueta={messages.guias.detalle.indicadoresNombres[i]}
              checked={f.indicadores.includes(i)}
              onCheckedChange={(marcado) => cambiar({ indicadores: marcado ? [...f.indicadores, i] : f.indicadores.filter((x) => x !== i) })}
            />
          ))}
        </fieldset>
      </Seccion>

      {conVehiculos ? (
        <Seccion titulo={t.vehiculos} ayuda={t.vehiculosAyuda}>
          <div className="flex flex-col gap-2">
            {f.vehiculos.map((placa, i) => (
              <div key={i} className="flex items-end gap-2">
                <Campo id={`guia-placa-${i}`} etiqueta={`${t.placa} ${i + 1}`} className="max-w-xs flex-1">
                  <input id={`guia-placa-${i}`} value={placa} onChange={(e) => cambiar({ vehiculos: reemplazar(f.vehiculos, i, e.target.value) })} maxLength={8} className={cn(CAMPO, "font-mono")} />
                </Campo>
                {i > 0 ? (
                  <button type="button" onClick={() => cambiar({ vehiculos: f.vehiculos.filter((_, n) => n !== i) })} aria-label={`${t.quitar} ${t.placa} ${i + 1}`} className={BOTON_SECUNDARIO}>
                    <TrashIcon className="size-4" />
                  </button>
                ) : null}
              </div>
            ))}
            {f.vehiculos.length < MAX_VEHICULOS ? (
              <button type="button" onClick={() => cambiar({ vehiculos: [...f.vehiculos, ""] })} className={cn(BOTON_SECUNDARIO, "w-fit")}>
                <PlusIcon weight="bold" className="size-4" />
                {t.agregar}
              </button>
            ) : null}
          </div>
        </Seccion>
      ) : null}

      {conVehiculos ? (
        <Seccion titulo={t.conductores} ayuda={t.conductoresAyuda}>
          <div className="flex flex-col gap-4">
            {f.conductores.map((c, i) => (
              <ConductorFila key={i} i={i} conductor={c} tipos={tiposDocumento} alCambiar={(nuevo) => cambiar({ conductores: reemplazar(f.conductores, i, nuevo) })} alQuitar={i > 0 ? () => cambiar({ conductores: f.conductores.filter((_, n) => n !== i) }) : undefined} />
            ))}
            {f.conductores.length < MAX_CONDUCTORES ? (
              <button type="button" onClick={() => cambiar({ conductores: [...f.conductores, conductorVacio()] })} className={cn(BOTON_SECUNDARIO, "w-fit")}>
                <PlusIcon weight="bold" className="size-4" />
                {t.agregar}
              </button>
            ) : null}
          </div>
        </Seccion>
      ) : null}

      <Seccion titulo={t.bienes} ayuda={t.bienesAyuda}>
        <div className="flex flex-col gap-4">
          {f.items.map((it, i) => (
            <ItemFila key={i} i={i} item={it} alCambiar={(nuevo) => cambiar({ items: reemplazar(f.items, i, nuevo) })} alQuitar={f.items.length > 1 ? () => cambiar({ items: f.items.filter((_, n) => n !== i) }) : undefined} />
          ))}
          <button type="button" onClick={() => cambiar({ items: [...f.items, itemVacio()] })} className={cn(BOTON_SECUNDARIO, "w-fit")}>
            <PlusIcon weight="bold" className="size-4" />
            {t.agregar}
          </button>
        </div>
      </Seccion>

      <Seccion titulo={t.documentos} ayuda={t.documentosAyuda}>
        <div className="flex flex-col gap-4">
          {f.documentos.map((d, i) => (
            <DocumentoFila key={i} i={i} documento={d} opciones={documentosAplicables} alCambiar={(nuevo) => cambiar({ documentos: reemplazar(f.documentos, i, nuevo) })} alQuitar={() => cambiar({ documentos: f.documentos.filter((_, n) => n !== i) })} />
          ))}
          {f.documentos.length < MAX_DOCUMENTOS ? (
            <button type="button" onClick={() => cambiar({ documentos: [...f.documentos, { ...documentoVacio(), tipo: documentosAplicables[0]?.codigo ?? "01" }] })} className={cn(BOTON_SECUNDARIO, "w-fit")}>
              <PlusIcon weight="bold" className="size-4" />
              {t.agregar}
            </button>
          ) : null}
        </div>
      </Seccion>

      <Seccion titulo={t.observaciones}>
        <Campo id="guia-observaciones" etiqueta={t.observaciones} ayuda={t.observacionesAyuda}>
          <input id="guia-observaciones" value={f.observaciones} onChange={(e) => cambiar({ observaciones: e.target.value })} maxLength={MAX_OBSERVACIONES} className={CAMPO} />
        </Campo>
        <Casilla
          id="guia-enviar-automatico"
          etiqueta={t.enviarAhora}
          descripcion={t.enviarAhoraAyuda}
          checked={f.enviar_automatico}
          onCheckedChange={(enviar_automatico) => cambiar({ enviar_automatico })}
        />
      </Seccion>

      {error ? (
        <p role="alert" className="text-cuerpo text-destructive" data-testid="guia-error">
          {error}
        </p>
      ) : null}
      {valida ? (
        <p role="status" className="text-cuerpo text-success-foreground" data-testid="guia-valida">
          {t.validaOk}
        </p>
      ) : null}

      <div className="flex flex-wrap items-center justify-end gap-2 border-t border-border/60 pt-4">
        <Link href="/guias" className={BOTON_SECUNDARIO}>
          {t.cancelar}
        </Link>
        <button type="button" onClick={() => void enviar("validar")} disabled={bloqueado} title={soloLectura ?? undefined} className={BOTON_SECUNDARIO}>
          {enviando === "validar" ? t.validando : t.validar}
        </button>
        <button type="submit" disabled={bloqueado} title={soloLectura ?? undefined} className={BOTON_PRIMARIO}>
          <PaperPlaneTiltIcon className="size-4" />
          {enviando === "emitir" ? t.emitiendo : t.emitir}
        </button>
      </div>
    </form>
  );
}

function ConductorFila({ i, conductor, tipos, alCambiar, alQuitar }: { i: number; conductor: ConductorForm; tipos: OpcionDeCatalogo[]; alCambiar: (c: ConductorForm) => void; alQuitar?: () => void }) {
  const t = useMessages().guias.form;
  const id = `guia-conductor-${i}`;
  return (
    <div className="grid grid-cols-1 gap-3 rounded-lg border border-border p-3 sm:grid-cols-6">
      <Campo id={`${id}-tipo`} etiqueta={t.tipoDoc} className="sm:col-span-2">
        {tipos.length > 0 ? (
          <Seleccion id={`${id}-tipo`} valor={conductor.tipo_doc} alCambiar={(v) => alCambiar({ ...conductor, tipo_doc: v })} opciones={tipos.map((o) => ({ valor: o.codigo, texto: textoDeOpcion(o) }))} />
        ) : (
          <input id={`${id}-tipo`} value={conductor.tipo_doc} onChange={(e) => alCambiar({ ...conductor, tipo_doc: e.target.value })} className={cn(CAMPO, "font-mono")} />
        )}
      </Campo>
      <Campo id={`${id}-num`} etiqueta={t.numDoc} className="sm:col-span-2">
        <input id={`${id}-num`} value={conductor.num_doc} onChange={(e) => alCambiar({ ...conductor, num_doc: e.target.value })} maxLength={15} className={cn(CAMPO, "font-mono")} />
      </Campo>
      <Campo id={`${id}-licencia`} etiqueta={t.licencia} className="sm:col-span-2">
        <input id={`${id}-licencia`} value={conductor.licencia} onChange={(e) => alCambiar({ ...conductor, licencia: e.target.value })} maxLength={10} className={cn(CAMPO, "font-mono")} />
      </Campo>
      <Campo id={`${id}-nombres`} etiqueta={t.conductorNombres} className="sm:col-span-3">
        <input id={`${id}-nombres`} value={conductor.nombres} onChange={(e) => alCambiar({ ...conductor, nombres: e.target.value })} maxLength={250} className={CAMPO} />
      </Campo>
      <Campo id={`${id}-apellidos`} etiqueta={t.conductorApellidos} className="sm:col-span-3">
        <input id={`${id}-apellidos`} value={conductor.apellidos} onChange={(e) => alCambiar({ ...conductor, apellidos: e.target.value })} maxLength={250} className={CAMPO} />
      </Campo>
      {alQuitar ? (
        <button type="button" onClick={alQuitar} className={cn(BOTON_SECUNDARIO, "w-fit sm:col-span-6")}>
          <TrashIcon className="size-4" />
          {t.quitar}
        </button>
      ) : null}
    </div>
  );
}

function ItemFila({ i, item, alCambiar, alQuitar }: { i: number; item: ItemForm; alCambiar: (it: ItemForm) => void; alQuitar?: () => void }) {
  const t = useMessages().guias.form;
  const id = `guia-item-${i}`;
  return (
    <div className="grid grid-cols-1 gap-3 rounded-lg border border-border p-3 sm:grid-cols-6">
      <Campo id={`${id}-descripcion`} etiqueta={t.descripcion} className="sm:col-span-6">
        <input id={`${id}-descripcion`} value={item.descripcion} onChange={(e) => alCambiar({ ...item, descripcion: e.target.value })} maxLength={500} className={CAMPO} />
      </Campo>
      <Campo id={`${id}-cantidad`} etiqueta={t.cantidad} className="sm:col-span-2">
        <input id={`${id}-cantidad`} inputMode="decimal" value={item.cantidad} onChange={(e) => alCambiar({ ...item, cantidad: e.target.value })} className={cn(CAMPO, "font-mono")} />
      </Campo>
      <Campo id={`${id}-unidad`} etiqueta={t.unidad} ayuda={i === 0 ? t.unidadAyuda : undefined} className="sm:col-span-2">
        <input id={`${id}-unidad`} value={item.unidad} onChange={(e) => alCambiar({ ...item, unidad: e.target.value.toUpperCase() })} maxLength={3} className={cn(CAMPO, "font-mono")} />
      </Campo>
      <Campo id={`${id}-codigo`} etiqueta={t.codigoOpcional} className="sm:col-span-1">
        <input id={`${id}-codigo`} value={item.codigo} onChange={(e) => alCambiar({ ...item, codigo: e.target.value })} maxLength={30} className={cn(CAMPO, "font-mono")} />
      </Campo>
      <Campo id={`${id}-codigo-sunat`} etiqueta={t.codigoSunat} className="sm:col-span-1">
        <input id={`${id}-codigo-sunat`} value={item.codigo_sunat} onChange={(e) => alCambiar({ ...item, codigo_sunat: e.target.value })} maxLength={8} inputMode="numeric" className={cn(CAMPO, "font-mono")} />
      </Campo>
      {alQuitar ? (
        <button type="button" onClick={alQuitar} className={cn(BOTON_SECUNDARIO, "w-fit sm:col-span-6")}>
          <TrashIcon className="size-4" />
          {t.quitar}
        </button>
      ) : null}
    </div>
  );
}

function DocumentoFila({ i, documento, opciones, alCambiar, alQuitar }: { i: number; documento: DocumentoForm; opciones: OpcionDeCatalogo[]; alCambiar: (d: DocumentoForm) => void; alQuitar: () => void }) {
  const t: Mensajes["guias"]["form"] = useMessages().guias.form;
  const id = `guia-documento-${i}`;
  return (
    <div className="grid grid-cols-1 gap-3 rounded-lg border border-border p-3 sm:grid-cols-6">
      <Campo id={`${id}-tipo`} etiqueta={t.documentoTipo} className="sm:col-span-3">
        {opciones.length > 0 ? (
          <Seleccion id={`${id}-tipo`} valor={documento.tipo} alCambiar={(v) => alCambiar({ ...documento, tipo: v })} opciones={opciones.map((o) => ({ valor: o.codigo, texto: textoDeOpcion(o) }))} />
        ) : (
          <input id={`${id}-tipo`} value={documento.tipo} onChange={(e) => alCambiar({ ...documento, tipo: e.target.value })} maxLength={2} className={cn(CAMPO, "font-mono")} />
        )}
      </Campo>
      <Campo id={`${id}-numero`} etiqueta={t.documentoNumero} className="sm:col-span-2">
        <input id={`${id}-numero`} value={documento.numero} onChange={(e) => alCambiar({ ...documento, numero: e.target.value })} maxLength={100} className={cn(CAMPO, "font-mono")} />
      </Campo>
      <Campo id={`${id}-ruc`} etiqueta={t.documentoRuc} className="sm:col-span-1">
        <input id={`${id}-ruc`} value={documento.ruc_emisor} onChange={(e) => alCambiar({ ...documento, ruc_emisor: e.target.value })} maxLength={11} className={cn(CAMPO, "font-mono")} />
      </Campo>
      <button type="button" onClick={alQuitar} className={cn(BOTON_SECUNDARIO, "w-fit sm:col-span-6")}>
        <TrashIcon className="size-4" />
        {t.quitar}
      </button>
    </div>
  );
}
