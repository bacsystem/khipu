import type { ReactNode } from "react";
import { Shell } from "khipu-design-system/nav/shell";
import { redirect } from "next/navigation";
import { RevisaTuCorreo } from "@/components/auth/revisa-tu-correo";
import { BannerDeMantenimiento } from "@/components/banner-de-mantenimiento";
import { debeAceptarTerminos, PantallaDeTerminos } from "@/components/legal/pantalla-de-terminos";
import { AvisoDeSoporte } from "@/components/soporte/aviso-de-soporte";
import { AyudaDeSoporte } from "@/components/soporte/ayuda-de-soporte";
import { SidebarContent } from "@/components/nav/sidebar-content";
import { TopBar } from "@/components/nav/top-bar";
import { TextosDeCliente } from "@/i18n/textos-de-cliente";
import { me } from "@/lib/api/auth";
import { obtenerBannerVigente } from "@/lib/api/banner";
import { apiPublicUrl } from "@/lib/api/client";
import { obtenerMiCuenta } from "@/lib/api/cuenta";
import { esCuentaSuspendida, RUTA_CUENTA_SUSPENDIDA } from "@/lib/api/cuenta-suspendida";
import { listarEmpresas } from "@/lib/api/empresas";
import { ApiError } from "@/lib/api/types";
import { getServerSession } from "@/lib/session-server";
import { SoloLectura } from "@/lib/solo-lectura";

export default async function PrivadoLayout({ children }: { children: ReactNode }) {
  const { access, empresaId } = await getServerSession();
  if (!access) redirect("/login");

  // Una cuenta suspendida (#182) conserva su sesión pero el backend le niega todo: se le explica en vez de dejar que la página falle sin decir por qué.
  // El `redirect` va fuera del `.then`, porque lanza y no debe confundirse con un fallo del backend.
  // El aviso de mantenimiento (#199) nunca falla: sin él la página carga igual.
  // El plan y el consumo (C1) tampoco tumban el panel: sin ellos el menú dice que no se pudo leer el plan.
  const cargado = await Promise.all([me(access), listarEmpresas(access), obtenerBannerVigente(), obtenerMiCuenta(access).catch(() => null)]).then(
    (datos) => ({ datos }),
    (error: unknown) => ({ error }),
  );
  if ("error" in cargado) {
    if (esCuentaSuspendida(cargado.error)) redirect(RUTA_CUENTA_SUSPENDIDA);
    // Un 401 es una sesión muerta (p. ej. una sesión de soporte que venció entre el middleware y este render): al login, no a una página de error.
    if (cargado.error instanceof ApiError && cargado.error.status === 401) redirect("/login");
    throw cargado.error;
  }
  const [usuario, empresas, banner, cuenta] = cargado.datos;
  // #342: sin la versión vigente de los términos aceptada no se usa el panel. En la misma URL, para que al aceptar siga donde quería ir; antes del
  // onboarding, que tampoco se hace sin aceptar.
  if (debeAceptarTerminos(usuario))
    return (
      <TextosDeCliente segmento="privado">
        <PantallaDeTerminos />
      </TextosDeCliente>
    );
  if (empresas.length === 0) redirect("/onboarding");
  const activa = empresas.find((empresa) => empresa.id === empresaId) ?? empresas[0];

  return (
    <TextosDeCliente segmento="privado">
      <div className="flex min-h-screen flex-col">
        {/* Una sesión de soporte (#184) lo dice arriba de todo, fijo, en cada página: es lo que evita confundirla con la sesión normal del cliente. */}
        {usuario.soporte_hasta ? <AvisoDeSoporte email={usuario.email} hasta={usuario.soporte_hasta} cuentaId={usuario.cuenta_id} /> : null}
        {/* El aviso de mantenimiento que publica el backoffice (#199): lo ve todo cliente, en cada página, hasta que vence o se retira. */}
        {banner ? <BannerDeMantenimiento texto={banner.texto} hasta={banner.hasta} /> : null}
        {/* H10: en una sesión de soporte cada acción que escribe se ve deshabilitada, en vez de dejar llenar un formulario que el backend va a rechazar. */}
        {/* #21: el rol del usuario también acota lo que puede hacer; el backend lo vuelve a decidir en cada petición. */}
        <SoloLectura activa={Boolean(usuario.soporte_hasta)} rol={usuario.rol}>
        <Shell
          sidebar={<SidebarContent usuario={usuario} cuenta={cuenta} empresas={empresas} activaId={activa?.id} />}
          topBar={<TopBar entorno={activa.entorno} usuario={usuario} cuenta={cuenta} empresas={empresas} activaId={activa?.id} apiBaseUrl={apiPublicUrl()} />}
        >
          {/* Con el correo sin verificar (#22) el backend rechaza toda escritura: se dice antes de que algo falle sin explicación. */}
          {usuario.correo_verificado ? null : (
            <div className="mb-3">
              <RevisaTuCorreo email={usuario.email} />
            </div>
          )}
          {children}
          {/* #250: a dónde escribir si algo no sale. Sin SUPPORT_URL ni SUPPORT_EMAIL no se muestra. */}
          <AyudaDeSoporte className="mt-4 px-1 pb-1" />
        </Shell>
        </SoloLectura>
      </div>
    </TextosDeCliente>
  );
}
