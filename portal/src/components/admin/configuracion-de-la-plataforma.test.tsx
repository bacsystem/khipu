import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { BannerConfigurado, PlantillaDeCorreo, RemitenteConfigurado } from "@/lib/api/admin-configuracion";
import { ConfiguracionDeLaPlataforma } from "./configuracion-de-la-plataforma";

vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn(), push: vi.fn() }) }));
vi.mock("@/lib/api/browser", () => ({ apiRequest: vi.fn() }));

const AHORA = "2026-10-15T17:00:00.000Z";
const REMITENTE: RemitenteConfigurado = { vigente: { email: "no-responder@khipu.pe" }, personalizado: false, predeterminado: { email: "no-responder@khipu.pe" } };

function plantilla(tipo: string, etiqueta: string, p: Partial<PlantillaDeCorreo> = {}): PlantillaDeCorreo {
  return { tipo, etiqueta, cuando_se_manda: "x", vigente: { asunto: `Asunto de ${tipo}`, cuerpo: "Entra a {enlace}" }, defecto: { asunto: "a", cuerpo: "b" }, personalizada: false, variables: [], ...p };
}

const PLANTILLAS = [plantilla("VERIFICACION_CORREO", "Verificación de correo"), plantilla("RECUPERACION_CLAVE", "Restablecer la contraseña"), plantilla("BIENVENIDA", "Bienvenida")];

afterEach(cleanup);

/** Cada sección dibuja solo lo suyo, y los formularios empiezan de nuevo cuando el servidor trae datos nuevos. */
describe("ConfiguracionDeLaPlataforma (#199)", () => {
  it("el correo saliente muestra el formulario del remitente y marca su sección", () => {
    render(<ConfiguracionDeLaPlataforma contenido={{ seccion: "correo", remitente: REMITENTE }} ahora={AHORA} />);

    expect(screen.getByTestId("correo-vigente")).toBeTruthy();
    expect(screen.queryByTestId("plantilla-editor")).toBeNull();
    expect(screen.queryByTestId("aviso-actual")).toBeNull();
    expect(screen.getByRole("link", { name: "Correo saliente" }).getAttribute("aria-current")).toBe("page");
  });

  it("las plantillas muestran la lista y el editor del correo elegido", () => {
    render(<ConfiguracionDeLaPlataforma contenido={{ seccion: "plantillas", plantillas: PLANTILLAS, elegida: "BIENVENIDA" }} ahora={AHORA} />);

    expect(screen.getAllByTestId("plantilla-enlace")).toHaveLength(3);
    expect(screen.getByTestId("plantilla-editor").getAttribute("data-tipo")).toBe("BIENVENIDA");
    expect((screen.getByTestId("plantilla-asunto") as HTMLInputElement).value).toBe("Asunto de BIENVENIDA");
    expect(screen.queryByTestId("correo-vigente")).toBeNull();
    expect(screen.getByRole("link", { name: "Plantillas" }).getAttribute("aria-current")).toBe("page");
  });

  it("sin correo elegido, o con uno que no existe, se edita el primero", () => {
    const { unmount } = render(<ConfiguracionDeLaPlataforma contenido={{ seccion: "plantillas", plantillas: PLANTILLAS }} ahora={AHORA} />);
    expect(screen.getByTestId("plantilla-editor").getAttribute("data-tipo")).toBe("VERIFICACION_CORREO");
    unmount();

    render(<ConfiguracionDeLaPlataforma contenido={{ seccion: "plantillas", plantillas: PLANTILLAS, elegida: "NO_EXISTE" }} ahora={AHORA} />);
    expect(screen.getByTestId("plantilla-editor").getAttribute("data-tipo")).toBe("VERIFICACION_CORREO");
  });

  it("el correo elegido es el que se marca en la lista", () => {
    render(<ConfiguracionDeLaPlataforma contenido={{ seccion: "plantillas", plantillas: PLANTILLAS, elegida: "RECUPERACION_CLAVE" }} ahora={AHORA} />);

    expect(screen.getAllByTestId("plantilla-enlace").filter((e) => e.getAttribute("aria-current") === "page").map((e) => e.getAttribute("data-tipo"))).toEqual(["RECUPERACION_CLAVE"]);
  });

  it("sin ningún correo no dibuja la sección en lugar de romper", () => {
    render(<ConfiguracionDeLaPlataforma contenido={{ seccion: "plantillas", plantillas: [] }} ahora={AHORA} />);

    expect(screen.queryByTestId("plantilla-editor")).toBeNull();
    expect(screen.queryAllByTestId("plantilla-enlace")).toHaveLength(0);
  });

  it("el aviso muestra su formulario, con «no hay ninguno» si no hay", () => {
    render(<ConfiguracionDeLaPlataforma contenido={{ seccion: "aviso", banner: null }} ahora={AHORA} />);

    expect(screen.getByTestId("aviso-ninguno")).toBeTruthy();
    expect(screen.queryByTestId("correo-vigente")).toBeNull();
    expect(screen.getByRole("link", { name: "Aviso de mantenimiento" }).getAttribute("aria-current")).toBe("page");
  });

  it("el aviso publicado se ve con su estado", () => {
    const banner: BannerConfigurado = { texto: "Mantenimiento", desde: "2026-10-15T16:00:00Z", hasta: "2026-10-16T04:00:00Z", actualizado_en: "2026-10-15T15:00:00Z", vigente_ahora: true };
    render(<ConfiguracionDeLaPlataforma contenido={{ seccion: "aviso", banner }} ahora={AHORA} />);

    expect(screen.getByTestId("aviso-estado").getAttribute("data-estado")).toBe("vigente");
  });

  /** Lo escrito y no guardado no debe sobrevivir a una recarga con datos nuevos: la `key` cambia con lo que el servidor guardó. */
  it("un remitente guardado de nuevo vuelve a empezar desde lo guardado", () => {
    const { rerender } = render(<ConfiguracionDeLaPlataforma contenido={{ seccion: "correo", remitente: REMITENTE }} ahora={AHORA} />);
    expect((screen.getByTestId("correo-email") as HTMLInputElement).value).toBe("no-responder@khipu.pe");

    rerender(
      <ConfiguracionDeLaPlataforma
        contenido={{ seccion: "correo", remitente: { vigente: { email: "avisos@khipu.pe" }, personalizado: true, actualizado_en: "2026-10-15T20:00:00Z", predeterminado: REMITENTE.predeterminado } }}
        ahora={AHORA}
      />,
    );

    expect((screen.getByTestId("correo-email") as HTMLInputElement).value).toBe("avisos@khipu.pe");
  });

  it("una plantilla restaurada vuelve a empezar desde el texto de fábrica", () => {
    const editada = plantilla("BIENVENIDA", "Bienvenida", { personalizada: true, actualizada_en: "2026-10-15T20:00:00Z", vigente: { asunto: "Editado", cuerpo: "Entra a {enlace}" } });
    const { rerender } = render(<ConfiguracionDeLaPlataforma contenido={{ seccion: "plantillas", plantillas: [editada], elegida: "BIENVENIDA" }} ahora={AHORA} />);
    expect((screen.getByTestId("plantilla-asunto") as HTMLInputElement).value).toBe("Editado");

    rerender(<ConfiguracionDeLaPlataforma contenido={{ seccion: "plantillas", plantillas: [plantilla("BIENVENIDA", "Bienvenida", { vigente: { asunto: "De fábrica", cuerpo: "Entra a {enlace}" } })], elegida: "BIENVENIDA" }} ahora={AHORA} />);

    expect((screen.getByTestId("plantilla-asunto") as HTMLInputElement).value).toBe("De fábrica");
  });

  it("un aviso retirado vuelve a empezar con el formulario en blanco", () => {
    const banner: BannerConfigurado = { texto: "Mantenimiento", desde: "2026-10-15T16:00:00Z", hasta: "2026-10-16T04:00:00Z", actualizado_en: "2026-10-15T15:00:00Z", vigente_ahora: true };
    const { rerender } = render(<ConfiguracionDeLaPlataforma contenido={{ seccion: "aviso", banner }} ahora={AHORA} />);
    expect((screen.getByTestId("aviso-texto") as HTMLInputElement).value).toBe("Mantenimiento");

    rerender(<ConfiguracionDeLaPlataforma contenido={{ seccion: "aviso", banner: null }} ahora={AHORA} />);

    expect((screen.getByTestId("aviso-texto") as HTMLInputElement).value).toBe("");
  });
});
