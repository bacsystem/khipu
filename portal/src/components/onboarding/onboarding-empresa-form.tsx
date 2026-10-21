"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "@/components/forms/form-field";
import { Alerta } from "@/components/feedback/alerta";
import { BotonAsync } from "@/components/patrones/boton-async";
import { apiRequest, noSeSabeSiLlego } from "@/lib/api/browser";
import { enlaceASeccion } from "@/lib/empresa/secciones";
import { BOTON_PRIMARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";
import { razonSocialSchema, rucSchema, soloDigitos } from "@/lib/validacion";

const empresaSchema = z.object({
  ruc: rucSchema,
  razon_social: razonSocialSchema,
  entorno: z.enum(["BETA", "PRODUCCION"]),
});
type EmpresaValues = z.infer<typeof empresaSchema>;

/**
 * El alta de la empresa: lo único que el portal necesita para existir (#371). El certificado, las credenciales SOL y las
 * series se configuran ya dentro, en Empresa → Fiscal y certificado y en Series, que es donde el cliente también los
 * renueva; el asistente de tres pasos duplicaba esa configuración y pedía tener el `.p12` y la clave SOL a mano antes de
 * poder entrar.
 */
export function OnboardingEmpresaForm() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);
  const revisada = useRef(false);

  /**
   * Con una empresa ya creada no hay nada que pedir aquí: recargar (o volver atrás) reenviaba el mismo RUC y daba 409
   * «Ya existe una cuenta con ese correo» —el mensaje equivocado—. Se fija la empresa activa y se entra al portal, que
   * abre la primera sección pendiente. La cookie se fija antes de navegar: sin ella `/empresa` devuelve aquí y es un bucle.
   */
  useEffect(() => {
    // Una sola vez: con `router` entre las dependencias el efecto se repetía y salían varios POST de sesión, uno de ellos abortado por la navegación.
    if (revisada.current) return;
    revisada.current = true;
    (async () => {
      let sigue = true;
      try {
        const lista = await apiRequest<Array<{ id: string }>>("/api/proxy/empresas", { method: "GET" });
        const empresa = lista.estado === "exito" ? lista.datos?.[0] : null;
        if (!empresa) return;
        const sesion = await apiRequest("/api/session/empresa", { method: "POST", body: { empresaId: empresa.id } });
        if (sesion.estado !== "exito") return;
        sigue = false;
        router.replace(enlaceASeccion("certificado"));
        router.refresh();
      } catch {
        // Sin conexión se muestra el formulario: el POST de empresa avisará si ya existe.
      } finally {
        if (sigue) setCargando(false);
      }
    })();
  }, [router]);

  /**
   * Un corte de conexión no dice si el paso se completó, y el envío fallaba en silencio (el botón volvía a su estado y no
   * aparecía nada). Devuelve `null` cuando no se sabe, con el aviso ya puesto. El cliente no lanza: devuelve el sobre de
   * error, así que la decisión se toma sobre el código y no en un `catch`.
   */
  async function enviar<T>(ruta: string, init: { method: string; body?: unknown }) {
    const res = await apiRequest<T>(ruta, init);
    if (noSeSabeSiLlego(res)) {
      setError("Se cortó la conexión y no sabemos si el paso se completó. Recarga la página: si la empresa quedó creada, entrarás al portal.");
      return null;
    }
    return res;
  }

  const empresaForm = useForm<EmpresaValues>({
    resolver: zodResolver(empresaSchema),
    defaultValues: { entorno: "BETA" },
  });

  async function onEmpresa(values: EmpresaValues) {
    setError(null);
    const res = await enviar<{ id: string }>("/api/proxy/empresas", { method: "POST", body: values });
    if (!res) return;
    if (res.estado !== "exito" || !res.datos) {
      // El 409 de una empresa ya registrada no es «ya existe una cuenta con ese correo»: es este RUC.
      setError(res.codigo === "DUPLICADO" ? `Ya hay una empresa registrada con el RUC ${values.ruc}. Si es tuya, entra desde el selector de empresa; si no, revisa el número.` : mensajeError(res.codigo));
      return;
    }
    const sesion = await enviar("/api/session/empresa", { method: "POST", body: { empresaId: res.datos.id } });
    // Sin esto la empresa quedaba creada, el formulario no avanzaba y no decía nada: el portal lee la empresa activa de la cookie.
    if (!sesion) return;
    router.push(enlaceASeccion("certificado"));
    router.refresh();
  }

  if (cargando) return <div className="mx-auto w-full max-w-lg py-16 text-center text-sm text-muted-foreground" role="status">Cargando tu configuración…</div>;

  return (
    <div className="mx-auto w-full max-w-lg">
      {error ? <div className="mb-4"><Alerta tono="error">{error}</Alerta></div> : null}

      <form className="grid gap-4" onSubmit={empresaForm.handleSubmit(onEmpresa)} noValidate>
        <FormField
          id="ruc"
          label="RUC"
          inputMode="numeric"
          maxLength={11}
          filtrar={soloDigitos}
          placeholder="20123456786"
          hint="11 dígitos. Lo verificamos acá mismo antes de enviarlo."
          register={empresaForm.register("ruc")}
          error={empresaForm.formState.errors.ruc?.message}
        />
        <FormField
          id="razon_social"
          label="Razón social"
          maxLength={1500}
          // Un pegado desde una planilla trae tabuladores: SUNAT los rechaza (4338) y la razón social no se puede
          // corregir después, así que se limpian al escribir en vez de avisar cuando ya es tarde.
          filtrar={(v) => v.replace(/[\u0000-\u001F\u007F]/g, " ")}
          hint="Tal como figura en tu ficha RUC. No se puede cambiar después."
          register={empresaForm.register("razon_social")}
          error={empresaForm.formState.errors.razon_social?.message}
        />
        <div className="grid gap-1.5">
          <label htmlFor="entorno" className={ETIQUETA_CAMPO}>Entorno SUNAT</label>
          <select
            id="entorno"
            {...empresaForm.register("entorno")}
            className={CAMPO}
          >
            <option value="BETA">Beta (pruebas)</option>
            <option value="PRODUCCION">Producción</option>
          </select>
          {/* El entorno no se puede cambiar después (no hay endpoint que lo edite): quien onboardea en Beta por descuido
              necesita intervención manual para emitir de verdad. */}
          <p className="text-[12px] text-muted-foreground">
            En <strong className="font-medium">Beta</strong> los comprobantes no tienen validez tributaria: es para probar.
            El entorno <strong className="font-medium">no se puede cambiar</strong> después de crear la empresa.
          </p>
        </div>
        <BotonAsync type="submit" pendiente={empresaForm.formState.isSubmitting} className={cn(BOTON_PRIMARIO, "w-full")}>
          Continuar
        </BotonAsync>
        <p className="text-center text-[12px] text-muted-foreground">
          Después cargas el certificado digital y las credenciales SOL desde el portal.
        </p>
      </form>
    </div>
  );
}
