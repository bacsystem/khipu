import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import es from "@/messages/es.json";
import { fijarLocaleDePrueba } from "./prueba";

vi.mock("next/navigation", () => ({
  usePathname: () => "/inicio",
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/lib/api/banner", () => ({ obtenerBannerVigente: vi.fn().mockResolvedValue(null) }));
vi.mock("@/lib/acceso", () => ({ registroAbierto: () => true }));
// La barra superior arrastra el menú del teléfono, que usa la server action de cambiar de empresa.
vi.mock("@/app/actions", () => ({ seleccionarEmpresa: vi.fn() }));

import LoginPage from "@/app/login/page";
import { RegistroForm } from "@/components/auth/registro-form";
import { AlertasDelPanel } from "@/components/inicio/alertas-del-panel";
import { KpisDelMes } from "@/components/inicio/kpis-del-mes";
import { TopBar } from "@/components/nav/top-bar";
import { SidebarContent } from "@/components/nav/sidebar-content";
import { CertificadoForm } from "@/components/empresa/certificado-form";
import { CredencialesSolForm } from "@/components/empresa/credenciales-sol-form";

afterEach(cleanup);

/**
 * #25: las pantallas ya migradas se ven enteras en el segundo locale. Además de buscar los textos ingleses, se comprueba que no quedó
 * ningún texto castellano de las mismas claves (un literal que se escapó a la migración sigue en castellano y lo delataría).
 */
function sinCastellano(contenedor: HTMLElement, textos: string[]) {
  const visible = contenedor.textContent ?? "";
  expect(textos.filter((t) => visible.includes(t))).toEqual([]);
}

describe("pantallas migradas en inglés (#25)", () => {
  it("el login: marca, formulario, enlaces y pie", async () => {
    fijarLocaleDePrueba("en");
    const { container } = render(await LoginPage());

    expect(screen.getByRole("heading", { name: en.auth.login.titulo })).toBeTruthy();
    expect(screen.getByText(en.auth.marca.titulo)).toBeTruthy();
    expect(screen.getByLabelText(en.auth.login.email)).toBeTruthy();
    expect(screen.getByLabelText(en.auth.login.password)).toBeTruthy();
    expect(screen.getByRole("button", { name: en.auth.login.enviar })).toBeTruthy();
    expect(screen.getByRole("link", { name: en.auth.login.olvidaste })).toBeTruthy();
    expect(screen.getByRole("link", { name: en.legal.terminos })).toBeTruthy();
    sinCastellano(container, [es.auth.login.subtitulo, es.auth.login.olvidaste, es.auth.marca.titulo, es.legal.privacidad]);
  });

  it("el registro, con la casilla de términos", () => {
    fijarLocaleDePrueba("en");
    const { container } = render(<RegistroForm />);

    expect(screen.getByLabelText(en.auth.registro.nombre)).toBeTruthy();
    expect(screen.getByLabelText(en.auth.registro.telefono)).toBeTruthy();
    expect(screen.getByText(en.auth.registro.ayudaPassword)).toBeTruthy();
    expect(screen.getByRole("button", { name: en.auth.registro.enviar })).toBeTruthy();
    sinCastellano(container, [es.auth.registro.ayudaTelefono, es.auth.registro.aceptoLos, es.auth.registro.enviar]);
  });

  it("el menú lateral y la barra superior", () => {
    fijarLocaleDePrueba("en");
    const { container } = render(
      <>
        <SidebarContent usuario={{ id: "u-1", email: "demo@example.com", rol: "ADMIN" } as never} cuenta={null} empresas={[]} />
        <TopBar entorno="BETA" usuario={{ id: "u-1", email: "demo@example.com", rol: "ADMIN" } as never} cuenta={null} empresas={[]} apiBaseUrl="http://localhost:8001" />
      </>,
    );

    // También los nombres que no se ven: el del menú, el de la miga y el del botón del cajón (#519; antes los fijaba el design system en castellano).
    expect(screen.getByRole("navigation", { name: en.nav.principal })).toBeTruthy();
    expect(screen.getByRole("navigation", { name: en.nav.ubicacion })).toBeTruthy();
    expect(screen.getByRole("button", { name: en.nav.abrirMenu })).toBeTruthy();
    for (const etiqueta of Object.values(en.nav.items)) expect(screen.getAllByText(etiqueta).length).toBeGreaterThan(0);
    expect(screen.getByRole("heading", { level: 1 }).textContent).toBe(en.nav.items.inicio);
    expect(screen.getByRole("heading", { level: 1 }).parentElement?.textContent).toContain(en.nav.migas.secciones.panel);
    expect(screen.getByTitle(en.nav.entorno.betaAyuda).textContent).toContain(en.nav.entorno.betaSunat);
    sinCastellano(container, [es.nav.grupos.emision, es.nav.items.series, es.nav.items.plan, es.nav.entorno.betaSunat]);
    expect(screen.queryByRole("navigation", { name: es.nav.ubicacion })).toBeNull();
  });

  it("el panel de inicio: indicadores y avisos", () => {
    fijarLocaleDePrueba("en");
    const atencion = { total: 3, rechazados: 1, errores_de_envio: 0, fuera_de_plazo: 0, boletas_sin_enviar: 2 };
    const { container } = render(
      <>
        <KpisDelMes
          resumen={{ facturado: [{ moneda: "PEN", total: 1180 }], emitidos: 4, aceptados_con_cdr: 2, en_camino: 1, atencion_requerida: atencion } as never}
          anterior={null}
        />
        <AlertasDelPanel alertas={[]} incompleto={false} />
      </>,
    );

    expect(screen.getByRole("region", { name: en.inicio.kpis.titulo })).toBeTruthy();
    expect(screen.getByText(en.inicio.kpis.pendientesAyuda)).toBeTruthy();
    expect(screen.getByText(en.inicio.avisos.sinAvisos)).toBeTruthy();
    expect(screen.getByTestId("kpi-atencion-detalle").textContent).toBe("1 rejected · 2 unsent receipts");
    expect(screen.getByTestId("kpi-facturado").textContent).toBe("S/ 1,180.00");
    sinCastellano(container, [es.inicio.kpis.aceptados, es.inicio.kpis.esteMes, es.atencion.boletaVarios.replace("{n} ", "")]);
  });

  // Los controles del design system traen sus nombres en castellano por defecto: el portal les pasa los traducidos.
  it("el ojo de la clave y la ✕ del certificado tienen su nombre en inglés", () => {
    fijarLocaleDePrueba("en");
    render(<CredencialesSolForm configuradas={false} />);
    fireEvent.click(screen.getByRole("button", { name: en.empresa.credenciales.mostrarClave }));
    expect(screen.getByRole("button", { name: en.empresa.credenciales.ocultarClave })).toBeTruthy();
    expect(screen.queryByRole("button", { name: es.empresa.credenciales.ocultarClave })).toBeNull();
    cleanup();

    render(<CertificadoForm tieneCertificado={false} />);
    expect(screen.getByRole("button", { name: en.empresa.certificado.mostrarContrasena })).toBeTruthy();
    expect(screen.getByText(en.empresa.certificado.arrastra)).toBeTruthy();
    const archivo = new File(["p12"], "empresa.p12", { type: "application/x-pkcs12" });
    fireEvent.change(document.getElementById("certificado-archivo") as HTMLInputElement, { target: { files: [archivo] } });
    expect(screen.getByRole("button", { name: en.empresa.certificado.quitarArchivo })).toBeTruthy();
    expect(screen.queryByRole("button", { name: es.empresa.certificado.quitarArchivo })).toBeNull();
  });

  it("sin fijar el locale todo sigue en castellano", async () => {
    render(await LoginPage());
    expect(screen.getByRole("heading", { name: es.auth.login.titulo })).toBeTruthy();
    expect(screen.getByRole("button", { name: es.auth.login.enviar })).toBeTruthy();
  });
});
