"use client";

import { FloppyDiskIcon, MapPinIcon } from "@phosphor-icons/react";
import { useRouter } from "next/navigation";
import { useMessages } from "next-intl";
import { type FormEvent, useState } from "react";
import { Casilla } from "khipu-design-system/formularios/casilla";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { useConsultaRuc } from "@/components/consulta-ruc/use-consulta-ruc";
import { apiRequest } from "@/lib/api/browser";
import type { Domicilio, EmpresaDetalle } from "@/lib/api/empresas";
import { BotonAsync } from "khipu-design-system/patrones/boton-async";
import { Campo, Entrada } from "khipu-design-system/formularios/campo";
import { BOTON_PRIMARIO } from "@/lib/estilos";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { UbigeoSelector } from "./ubigeo-selector";

/**
 * Domicilio fiscal (RegistrationAddress del emisor en cada XML) y cuenta de detracciones por defecto. El ubigeo se elige en
 * cascada departamento → provincia → distrito sobre el catálogo 13 (INEI), que se carga al abrir el formulario.
 */
/**
 * Sin ubigeo el PUT manda `domicilio: null`, y el backend reemplaza los tres valores: borra el domicilio guardado
 * **y** descarta la dirección recién escrita, informando éxito. Así que si el formulario tiene algo del domicilio
 * cargado, el distrito es obligatorio. Vaciar los tres campos sí borra el domicilio, que es deliberado.
 */
export function faltaElDistrito(ubigeo: string, direccion: string, urbanizacion: string): boolean {
  if (ubigeo) return false;
  return direccion.trim() !== "" || urbanizacion.trim() !== "";
}

export function DatosFiscalesForm({
  ruc,
  domicilio,
  cuentaDetracciones,
  nombreComercial,
  padronTasaEspecialIgv,
}: {
  /** El de la empresa: sin domicilio guardado, se busca el suyo en el padrón de SUNAT para sugerirlo (#575). */
  ruc?: string;
  domicilio: Domicilio | null;
  cuentaDetracciones: string | null;
  nombreComercial: string | null;
  padronTasaEspecialIgv: boolean;
}) {
  const router = useRouter();
  const messages = useMessages();
  const t = messages.empresa.datosFiscales;
  const opcional = messages.empresa.comun.opcional;
  const mensajeError = useMensajeError();
  const [ubigeo, setUbigeo] = useState(domicilio?.ubigeo ?? "");
  const [direccion, setDireccion] = useState(domicilio?.direccion ?? "");
  const [urbanizacion, setUrbanizacion] = useState(domicilio?.urbanizacion ?? "");
  const [establecimiento, setEstablecimiento] = useState(domicilio?.codigo_establecimiento ?? "0000");
  const [cuenta, setCuenta] = useState(cuentaDetracciones ?? "");
  const [nombre, setNombre] = useState(nombreComercial ?? "");
  const [tasaEspecial, setTasaEspecial] = useState(padronTasaEspecialIgv);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState(false);

  // #575: una empresa sin domicilio todavía ve el que SUNAT tiene en el padrón y lo usa con un clic. Es una sugerencia, no se rellena sola: es lo que va en
  // cada XML. Con un domicilio ya guardado no se consulta.
  const { resultado: padron } = useConsultaRuc(ruc ?? "", !domicilio?.ubigeo && !domicilio?.direccion);
  const sugerido = padron?.tipo === "encontrado" && padron.contribuyente.direccion && padron.contribuyente.ubigeo ? padron.contribuyente : null;
  const mostrarSugerido = sugerido !== null && !ubigeo && !direccion.trim();

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setOk(false);
    if (faltaElDistrito(ubigeo, direccion, urbanizacion)) {
      setError(t.faltaDistrito);
      return;
    }
    setEnviando(true);
    const res = await apiRequest<EmpresaDetalle>("/api/proxy/empresa/datos-fiscales", {
      method: "PUT",
      body: {
        domicilio: ubigeo ? { ubigeo, direccion, urbanizacion: urbanizacion || null, codigo_establecimiento: establecimiento || "0000" } : null,
        cuenta_detracciones: cuenta || null,
        nombre_comercial: nombre.trim() || null,
        padron_tasa_especial_igv: tasaEspecial,
      },
    });
    setEnviando(false);
    if (res.estado !== "exito") {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    setOk(true);
    router.refresh();
  }

  return (
    <form onSubmit={onSubmit} className="grid grid-cols-1 gap-4" data-testid="datos-fiscales">
      {mostrarSugerido ? (
        <Alerta
          tono="info"
          className="px-3 py-2"
          accion={
            <button
              type="button"
              className="text-control font-medium text-primary hover:underline"
              onClick={() => {
                setDireccion((sugerido.direccion ?? "").slice(0, 200));
                setUbigeo(sugerido.ubigeo ?? "");
              }}
            >
              {t.usarDomicilioSunat}
            </button>
          }
        >
          <span data-testid="domicilio-sunat">
            {t.domicilioSunat} <strong className="font-medium">{sugerido.direccion}</strong>
          </span>
        </Alerta>
      ) : null}
      <div className="grid grid-cols-1 items-start gap-4 sm:grid-cols-3">
        <UbigeoSelector value={ubigeo} onChange={setUbigeo} inicial={domicilio} />
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-6">
        <Campo id="dom-direccion" etiqueta={t.direccion} ayuda={t.direccionAyuda} className="sm:col-span-4">
          <div className="relative">
            <MapPinIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground/70" />
            <Entrada
              id="dom-direccion"
              value={direccion}
              onChange={(e) => setDireccion(e.target.value)}
              placeholder={t.direccionPlaceholder}
              required={Boolean(ubigeo)}
              minLength={3}
              maxLength={200}
              aria-describedby="dom-direccion-ayuda"
              className="pl-9"
            />
          </div>
        </Campo>
        <Campo id="dom-urbanizacion" etiqueta={<>{t.urbanizacion} <span className="font-normal text-muted-foreground">{opcional}</span></>} className="sm:col-span-2">
          <Entrada id="dom-urbanizacion" value={urbanizacion} onChange={(e) => setUrbanizacion(e.target.value)} maxLength={25} />
        </Campo>
      </div>

      <div className="grid grid-cols-1 items-start gap-4 sm:grid-cols-2">
        <Campo id="dom-establecimiento" etiqueta={t.establecimiento} ayuda={t.establecimientoAyuda}>
          <Entrada
            id="dom-establecimiento"
            value={establecimiento}
            onChange={(e) => setEstablecimiento(e.target.value.replace(/\D/g, "").slice(0, 4))}
            inputMode="numeric"
            pattern="\d{4}"
            mono
            aria-describedby="dom-establecimiento-ayuda"
          />
        </Campo>
        <Campo id="cuenta-detracciones" etiqueta={<>{t.cuentaDetracciones} <span className="font-normal text-muted-foreground">{opcional}</span></>} ayuda={t.cuentaDetraccionesAyuda}>
          <Entrada
            id="cuenta-detracciones"
            value={cuenta}
            onChange={(e) => setCuenta(e.target.value)}
            placeholder="00-000-123456"
            pattern="[0-9-]{8,20}"
            mono
            aria-describedby="cuenta-detracciones-ayuda"
          />
        </Campo>
        <Campo id="nombre-comercial" etiqueta={<>{t.nombreComercial} <span className="font-normal text-muted-foreground">{opcional}</span></>} ayuda={t.nombreComercialAyuda} className="sm:col-span-2">
          <Entrada
            id="nombre-comercial"
            value={nombre}
            onChange={(e) => setNombre(e.target.value)}
            placeholder={t.nombreComercialPlaceholder}
            maxLength={1500}
            aria-describedby="nombre-comercial-ayuda"
          />
        </Campo>
        <div className="sm:col-span-2">
          <Casilla id="padron-tasa-especial" etiqueta={t.padron} descripcion={t.padronAyuda} checked={tasaEspecial} onCheckedChange={setTasaEspecial} className="select-none" />
        </div>
      </div>

      {error ? <p className="text-cuerpo text-destructive">{error}</p> : null}
      {ok ? <p className="text-cuerpo text-success-foreground">{t.actualizados}</p> : null}

      <div className="flex justify-end border-t border-border/60 pt-4">
        <BotonAsync type="submit" pendiente={enviando} icon={<FloppyDiskIcon className="size-4" />} textoPendiente={messages.empresa.comun.guardando} className={BOTON_PRIMARIO}>
          {t.guardar}
        </BotonAsync>
      </div>
    </form>
  );
}
