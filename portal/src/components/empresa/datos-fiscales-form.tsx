"use client";

import { MapPinIcon, SaveIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { apiRequest } from "@/lib/api/browser";
import type { Domicilio, EmpresaDetalle } from "@/lib/api/empresas";
import { AYUDA_CAMPO, BOTON_PRIMARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";
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
  domicilio,
  cuentaDetracciones,
  nombreComercial,
  padronTasaEspecialIgv,
}: {
  domicilio: Domicilio | null;
  cuentaDetracciones: string | null;
  nombreComercial: string | null;
  padronTasaEspecialIgv: boolean;
}) {
  const router = useRouter();
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

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setOk(false);
    if (faltaElDistrito(ubigeo, direccion, urbanizacion)) {
      setError("Elige el distrito. Sin ubigeo, SUNAT no acepta el domicilio (regla 4093) y la dirección no se guarda.");
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
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <UbigeoSelector value={ubigeo} onChange={setUbigeo} inicial={domicilio} />
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-6">
        <div className="flex flex-col gap-1.5 sm:col-span-4">
          <label htmlFor="dom-direccion" className={ETIQUETA_CAMPO}>
            Dirección
          </label>
          <div className="relative">
            <MapPinIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground/70" />
            <input
              id="dom-direccion"
              value={direccion}
              onChange={(e) => setDireccion(e.target.value)}
              placeholder="Av. Javier Prado Este 123 Of. 501"
              required={Boolean(ubigeo)}
              minLength={3}
              maxLength={200}
              className={cn(CAMPO, "pl-9")}
            />
          </div>
          <span className={AYUDA_CAMPO}>Tal como figura en la ficha RUC, en una sola línea (3 a 200 caracteres)</span>
        </div>
        <div className="flex flex-col gap-1.5 sm:col-span-2">
          <label htmlFor="dom-urbanizacion" className={ETIQUETA_CAMPO}>
            Urbanización <span className="font-normal text-muted-foreground">(opcional)</span>
          </label>
          <input id="dom-urbanizacion" value={urbanizacion} onChange={(e) => setUrbanizacion(e.target.value)} maxLength={25} className={CAMPO} />
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="dom-establecimiento" className={ETIQUETA_CAMPO}>
            Código de establecimiento anexo
          </label>
          <input
            id="dom-establecimiento"
            value={establecimiento}
            onChange={(e) => setEstablecimiento(e.target.value.replace(/\D/g, "").slice(0, 4))}
            inputMode="numeric"
            pattern="\d{4}"
            className={cn(CAMPO, "font-mono")}
          />
          <span className={AYUDA_CAMPO}>0000 = domicilio fiscal; otro código debe existir en la ficha RUC</span>
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor="cuenta-detracciones" className={ETIQUETA_CAMPO}>
            Cuenta de detracciones (Banco de la Nación) <span className="font-normal text-muted-foreground">(opcional)</span>
          </label>
          <input
            id="cuenta-detracciones"
            value={cuenta}
            onChange={(e) => setCuenta(e.target.value)}
            placeholder="00-000-123456"
            pattern="[0-9-]{8,20}"
            className={cn(CAMPO, "font-mono")}
          />
          <span className={AYUDA_CAMPO}>Se usa cuando una factura con detracción no indica la cuenta</span>
        </div>
        <div className="flex flex-col gap-1.5 sm:col-span-2">
          <label htmlFor="nombre-comercial" className={ETIQUETA_CAMPO}>
            Nombre comercial <span className="font-normal text-muted-foreground">(opcional)</span>
          </label>
          <input id="nombre-comercial" value={nombre} onChange={(e) => setNombre(e.target.value)} placeholder="Andina Store" maxLength={1500} className={CAMPO} />
          <span className={AYUDA_CAMPO}>Va en el XML junto a la razón social (regla 4092); déjelo vacío si no usa uno</span>
        </div>
        <div className="flex flex-col gap-1.5 sm:col-span-2">
          <label htmlFor="padron-tasa-especial" className="inline-flex cursor-pointer items-center gap-2 self-start select-none">
            <input
              id="padron-tasa-especial"
              type="checkbox"
              checked={tasaEspecial}
              onChange={(e) => setTasaEspecial(e.target.checked)}
              className="size-4 rounded border-input"
              data-testid="padron-tasa-especial"
            />
            <span className={ETIQUETA_CAMPO}>Inscrita en el Padrón de Tasa Especial del IGV (restaurantes y hoteles)</span>
          </label>
          <span className={AYUDA_CAMPO}>
            Ley 31556: los comprobantes gravados salen con la tasa reducida vigente (10.5 %) en vez del 18 %. Márcalo solo si SUNAT lo incluyó en el padrón; si no, observará cada comprobante (4439)
          </span>
        </div>
      </div>

      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      {ok ? <p className="text-sm text-success-foreground">Datos fiscales actualizados: el domicilio irá en el XML de las próximas facturas.</p> : null}

      <div className="flex justify-end border-t border-border/60 pt-4">
        <button type="submit" disabled={enviando} className={cn(BOTON_PRIMARIO, "h-9 text-[12px]")}>
          <SaveIcon className="size-4" />
          {enviando ? "Guardando…" : "Guardar datos fiscales"}
        </button>
      </div>
    </form>
  );
}
