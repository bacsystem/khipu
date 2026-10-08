"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowLeftIcon, ArrowRightIcon, UserPlusIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { type ChangeEvent, type ComponentProps, type FormEvent, useEffect, useRef, useState } from "react";
import { type FieldErrors, type UseFormRegisterReturn, useForm } from "react-hook-form";
import { z } from "zod";
import { ApiKeyRevelada } from "@/components/api-keys/api-key-revelada";
import { Alerta } from "@/components/feedback/alerta";
import { Campo, Entrada } from "@/components/formularios/campo";
import { Pasos } from "@/components/navegacion/pasos";
import { CabeceraDialogo, PieDialogo } from "@/components/patrones/cabecera-dialogo";
import { IconoSeccion } from "@/components/patrones/cabecera-seccion";
import { Dialog, DialogContent, DialogTrigger } from "@/components/ui/dialog";
import type { AltaAsistidaCreada } from "@/lib/api/admin-alta";
import { apiRequest, noSeSabeSiLlego } from "@/lib/api/browser";
import type { ApiEnvelope } from "@/lib/api/types";
import { BOTON_PRIMARIO_PIE, BOTON_SECUNDARIO_PIE, CAMPO } from "@/lib/estilos";
import { CABECERA_IDEMPOTENCIA, intentoPara, type Intento } from "@/lib/idempotencia";
import { mensajeError, messages } from "@/lib/messages";
import { codigoSerie, MENSAJE_SERIE, razonSocialSchema, rucSchema, serieCoincideConTipo, soloDigitos, soloTelefono, telefonoSchema } from "@/lib/validacion";

const t = messages.admin.alta;

const schema = z
  .object({
    email: z
      .string()
      .transform((v) => v.trim())
      .refine((v) => v.length > 0, "Ingresa el correo del cliente")
      .refine((v) => v.length <= 254, "El correo admite hasta 254 caracteres")
      .pipe(z.email("Correo inválido")),
    nombre: z
      .string()
      .transform((v) => v.trim())
      .refine((v) => v.length > 0, "Ingresa el nombre del cliente")
      .refine((v) => v.length <= 150, "El nombre admite hasta 150 caracteres"),
    // Obligatorio: es el contacto directo con el cliente si la invitación no le llega. Se valida y normaliza como en el registro.
    telefono: z
      .string()
      .transform((v) => v.trim())
      .refine((v) => v.length > 0, "Ingresa el celular del cliente")
      .pipe(telefonoSchema),
    ruc: rucSchema,
    razon_social: razonSocialSchema,
    entorno: z.enum(["BETA", "PRODUCCION"]),
    tipo: z.enum(["01", "03"]),
    serie: z.string().transform((v) => v.toUpperCase().trim()),
  })
  .superRefine((v, ctx) => {
    if (!serieCoincideConTipo(v.tipo, v.serie)) ctx.addIssue({ code: "custom", path: ["serie"], message: MENSAJE_SERIE });
  });

type Valores = z.input<typeof schema>;
type Salida = z.output<typeof schema>;

const VALORES_INICIALES: Valores = { email: "", nombre: "", telefono: "", ruc: "", razon_social: "", entorno: "BETA", tipo: "01", serie: "F001" };

/** Los tres pasos del asistente y los campos que valida cada uno antes de dejar seguir. */
const PASOS: ReadonlyArray<{ titulo: string; descripcion: string; campos: ReadonlyArray<keyof Valores> }> = [
  { titulo: t.seccionCuenta, descripcion: t.subtituloCuenta, campos: ["email", "nombre", "telefono"] },
  { titulo: t.seccionEmpresa, descripcion: t.subtituloEmpresa, campos: ["ruc", "razon_social", "entorno"] },
  { titulo: t.seccionSerie, descripcion: t.subtituloSerie, campos: ["tipo", "serie"] },
];

const ULTIMO = PASOS.length - 1;

/** Lo que dijo el backend cuando es más específico que el texto por código: un 409 puede ser el correo o el RUC. */
function textoDeError(res: ApiEnvelope<unknown>): string {
  if (res.codigo === "NO_AUTORIZADO") return t.sesionExpirada;
  // Con la clave de idempotencia (#219) reenviar el mismo formulario es seguro: si el alta se hizo, vuelve la misma API key.
  if (noSeSabeSiLlego(res)) return t.corteDeConexion;
  if (res.codigo === "DUPLICADO" && res.mensaje) return res.mensaje;
  return mensajeError(res.codigo);
}

/**
 * Alta asistida de un cliente (#188) en tres pasos: cuenta, primera empresa y primera serie. Cada paso valida sus campos antes de dejar
 * seguir; los tres quedan montados (solo se ocultan), así que volver atrás no pierde lo escrito. El administrador nunca define una
 * contraseña: el cliente la elige con la invitación que recibe por correo. La API key inicial se muestra una sola vez.
 *
 * Es el cuerpo del modal ({@link AltaAsistidaDialog}): `alCancelar` y `alTerminar` lo cierran.
 */
export function AltaAsistidaForm({
  alCancelar,
  alTerminar,
  alCambiarEnvio,
}: {
  alCancelar?: () => void;
  alTerminar?: () => void;
  /** Avisa cuándo empieza y termina el envío, para que el modal no se cierre a mitad (el administrador debe ver el resultado). */
  alCambiarEnvio?: (enviando: boolean) => void;
}) {
  const [paso, setPaso] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [creada, setCreada] = useState<{ datos: AltaAsistidaCreada; email: string } | null>(null);
  // Clave de idempotencia (#219): la misma mientras se reenvíe el mismo formulario, otra si cambia o se da de alta a otro cliente.
  const intento = useRef<Intento | null>(null);
  const {
    register,
    handleSubmit,
    reset,
    trigger,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<Valores, unknown, Salida>({ resolver: zodResolver(schema), defaultValues: VALORES_INICIALES });
  useEffect(() => alCambiarEnvio?.(isSubmitting), [isSubmitting, alCambiarEnvio]);

  async function onSubmit(values: Salida) {
    setError(null);
    const cuerpo = {
      nombre: values.nombre,
      email: values.email,
      telefono: values.telefono,
      empresa: { ruc: values.ruc, razon_social: values.razon_social, entorno: values.entorno },
      serie: { tipo: values.tipo, serie: values.serie },
    };
    intento.current = intentoPara(intento.current, JSON.stringify(cuerpo));
    const res = await apiRequest<AltaAsistidaCreada>("/api/admin/cuentas", {
      method: "POST",
      body: cuerpo,
      headers: { [CABECERA_IDEMPOTENCIA]: intento.current.clave },
    });
    if (res.estado === "exito" && res.datos) {
      setCreada({ datos: res.datos, email: values.email });
      return;
    }
    setError(textoDeError(res));
  }

  /** Si al enviar queda un error en un paso anterior (no debería: cada paso valida al avanzar), se vuelve a ese paso para verlo. */
  function alFallar(errores: FieldErrors<Valores>) {
    const conError = PASOS.findIndex((p) => p.campos.some((c) => errores[c]));
    if (conError >= 0) setPaso(conError);
  }

  async function siguiente() {
    if (await trigger([...PASOS[paso].campos])) setPaso((p) => Math.min(p + 1, ULTIMO));
  }

  // Enter en un campo de un paso intermedio avanza (validando ese paso), no envía: el alta sale solo desde el último.
  function enviar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (paso < ULTIMO) void siguiente();
    else void handleSubmit(onSubmit, alFallar)(e);
  }

  function otroCliente() {
    intento.current = null;
    reset(VALORES_INICIALES);
    setError(null);
    setCreada(null);
    setPaso(0);
  }

  if (creada) {
    const r = t.resultado;
    return (
      <>
        <div className="grid gap-4 px-5 py-4">
          <div className="flex items-center gap-2.5">
            <IconoSeccion icon={UserPlusIcon} className="bg-success text-success-foreground" />
            <div className="min-w-0">
              <h3 className="text-[13px] font-semibold text-foreground">{r.titulo}</h3>
              <p className="text-[12px] text-muted-foreground">{r.descripcion}</p>
            </div>
          </div>
          <ApiKeyRevelada apiKey={creada.datos.api_key} etiqueta={r.etiquetaApiKey} aviso={<p>{r.avisoApiKey}</p>} />
          <Alerta tono={creada.datos.invitacion_enviada ? "ok" : "aviso"}>
            {(creada.datos.invitacion_enviada ? r.invitacionEnviada : r.invitacionNoEnviada).replace("{email}", creada.email)}
          </Alerta>
        </div>
        <PieDialogo>
          <button type="button" onClick={otroCliente} className={BOTON_SECUNDARIO_PIE}>
            {r.otra}
          </button>
          <button type="button" onClick={alTerminar} className={BOTON_PRIMARIO_PIE}>
            {r.listo}
          </button>
        </PieDialogo>
      </>
    );
  }

  const tipo = watch("tipo");
  return (
    <form onSubmit={enviar} noValidate>
      <div className="grid gap-5 px-5 py-4">
        <Pasos pasos={PASOS.map(({ titulo, descripcion }) => ({ titulo, descripcion }))} actual={paso} />

        {/* Los tres pasos quedan montados y solo se oculta el que no toca: así lo escrito sobrevive a ir y volver. */}
        {/* Una columna: todos los campos del mismo ancho, en el orden en que se piensan (primero con qué correo entra el cliente). */}
        <fieldset hidden={paso !== 0} className="grid gap-4">
          <legend className="sr-only">{t.seccionCuenta}</legend>
          <CampoDeTexto id="alta-email" etiqueta={t.email} type="email" autoComplete="off" maxLength={254} registro={register("email")} error={errors.email?.message} ayuda={t.emailAyuda} />
          <CampoDeTexto id="alta-nombre" etiqueta={t.nombre} maxLength={150} placeholder={t.nombreEjemplo} registro={register("nombre")} error={errors.nombre?.message} ayuda={t.nombreAyuda} />
          <CampoDeTexto id="alta-telefono" etiqueta={t.telefono} inputMode="tel" autoComplete="off" placeholder="987654321" filtrar={soloTelefono} registro={register("telefono")} error={errors.telefono?.message} />
        </fieldset>

        <fieldset hidden={paso !== 1} className="grid gap-4">
          <legend className="sr-only">{t.seccionEmpresa}</legend>
          <CampoDeTexto id="alta-ruc" etiqueta={t.ruc} inputMode="numeric" maxLength={11} filtrar={soloDigitos} mono registro={register("ruc")} error={errors.ruc?.message} />
          <CampoDeTexto id="alta-razon-social" etiqueta={t.razonSocial} registro={register("razon_social")} error={errors.razon_social?.message} />
          <Campo id="alta-entorno" etiqueta={t.entorno}>
            <select id="alta-entorno" {...register("entorno")} className={CAMPO}>
              <option value="BETA">{t.entornoBeta}</option>
              <option value="PRODUCCION">{t.entornoProduccion}</option>
            </select>
          </Campo>
        </fieldset>

        <fieldset hidden={paso !== 2} className="grid gap-4">
          <legend className="sr-only">{t.seccionSerie}</legend>
          <Campo id="alta-tipo" etiqueta={t.tipo}>
            <select id="alta-tipo" {...register("tipo")} className={CAMPO}>
              <option value="01">{t.tipoFactura}</option>
              <option value="03">{t.tipoBoleta}</option>
            </select>
          </Campo>
          <CampoDeTexto
            id="alta-serie"
            etiqueta={t.serie}
            maxLength={4}
            filtrar={codigoSerie}
            mono
            placeholder={tipo === "03" ? "B001" : "F001"}
            registro={register("serie")}
            error={errors.serie?.message}
            ayuda={t.serieAyuda}
          />
        </fieldset>

        {error ? <Alerta tono="error">{error}</Alerta> : null}
      </div>

      <PieDialogo izquierda={<span className="font-mono text-[11px] text-muted-foreground">{t.pasoDe.replace("{n}", String(paso + 1)).replace("{total}", String(PASOS.length))}</span>}>
        {paso === 0 ? (
          <button type="button" onClick={alCancelar} disabled={isSubmitting} className={BOTON_SECUNDARIO_PIE}>
            {t.cancelar}
          </button>
        ) : (
          <button type="button" onClick={() => setPaso((p) => p - 1)} disabled={isSubmitting} className={BOTON_SECUNDARIO_PIE}>
            <ArrowLeftIcon className="size-4" />
            {t.atras}
          </button>
        )}
        {paso < ULTIMO ? (
          <button type="submit" className={BOTON_PRIMARIO_PIE}>
            {t.siguiente}
            <ArrowRightIcon className="size-4" />
          </button>
        ) : (
          <button type="submit" disabled={isSubmitting} className={BOTON_PRIMARIO_PIE}>
            <UserPlusIcon className="size-4" />
            {isSubmitting ? t.enviando : t.enviar}
          </button>
        )}
      </PieDialogo>
    </form>
  );
}

/**
 * «Nueva cuenta» como el resto de las altas del backoffice: un modal que se abre desde la cabecera. No se cierra mientras envía, y al
 * terminar recarga la lista de cuentas para que la nueva aparezca sin navegar.
 */
export function AltaAsistidaDialog({ claseDelBoton }: { claseDelBoton: string }) {
  const router = useRouter();
  const [abierto, setAbierto] = useState(false);
  const [enviando, setEnviando] = useState(false);
  // Cada apertura estrena formulario (paso 1, campos vacíos): la `key` remonta el cuerpo en vez de arrastrar lo del alta anterior.
  const [apertura, setApertura] = useState(0);

  function cambiarAbierto(valor: boolean) {
    if (enviando) return;
    setAbierto(valor);
    if (valor) setApertura((n) => n + 1);
    // Al cerrar, por la X o con «Listo», la lista vuelve a pedirse: si hubo alta, la cuenta nueva aparece sin navegar.
    else router.refresh();
  }

  return (
    // Un clic fuera no lo cierra: perdería lo escrito en tres pasos o, peor, la API key que solo se muestra una vez. Se cierra con la X,
    // Escape, «Cancelar» o «Listo», que son gestos deliberados.
    <Dialog open={abierto} onOpenChange={cambiarAbierto} disablePointerDismissal>
      <DialogTrigger className={claseDelBoton} data-testid="nueva-cuenta">
        <UserPlusIcon className="size-4" />
        {t.titulo}
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] gap-0 overflow-y-auto p-0 sm:max-w-lg" data-testid="alta-dialogo" showCloseButton={!enviando}>
        <CabeceraDialogo icon={UserPlusIcon} titulo={t.titulo} descripcion={t.descripcion} />
        <AltaAsistidaForm key={apertura} alCancelar={() => cambiarAbierto(false)} alTerminar={() => cambiarAbierto(false)} alCambiarEnvio={setEnviando} />
      </DialogContent>
    </Dialog>
  );
}

/**
 * Un campo de texto del formulario con las recetas del design system (`Campo` + `Entrada`, h-10): etiqueta, ayuda o error atados al
 * input por `aria-describedby`, y `filtrar` para corregir lo tecleado antes de que llegue al formulario (un RUC no acepta letras).
 */
function CampoDeTexto({
  id,
  etiqueta,
  ayuda,
  error,
  registro,
  filtrar,
  className,
  ...input
}: Omit<ComponentProps<"input">, "id" | "className"> & {
  id: string;
  etiqueta: string;
  ayuda?: string;
  error?: string;
  registro: UseFormRegisterReturn;
  filtrar?: (valor: string) => string;
  className?: string;
  mono?: boolean;
}) {
  const onChange = filtrar
    ? (e: ChangeEvent<HTMLInputElement>) => {
        e.target.value = filtrar(e.target.value);
        return registro.onChange(e);
      }
    : registro.onChange;
  return (
    <Campo id={id} etiqueta={etiqueta} ayuda={ayuda} error={error} className={className}>
      <Entrada id={id} invalido={!!error} aria-describedby={error ? `${id}-error` : ayuda ? `${id}-ayuda` : undefined} {...input} {...registro} onChange={onChange} />
    </Campo>
  );
}
