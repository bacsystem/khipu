import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { AccesosDeSoporte, duracionEnPalabras } from "./accesos-de-soporte";

afterEach(cleanup);

describe("duracionEnPalabras (#184)", () => {
  it("dice los minutos redondos en minutos y lo demás en segundos", () => {
    expect(duracionEnPalabras(900)).toBe("15 min");
    expect(duracionEnPalabras(60)).toBe("1 min");
    expect(duracionEnPalabras(90)).toBe("90 s");
    expect(duracionEnPalabras(1)).toBe("1 s");
  });
});

describe("AccesosDeSoporte (#184)", () => {
  it("cada acceso dice cuándo, como qué usuario y por cuánto tiempo como máximo", () => {
    render(<AccesosDeSoporte accesos={[{ ocurrido_en: "2026-10-04T15:00:00Z", usuario: "ana@negocio.pe", duracion_segundos: 900 }]} />);

    const fila = screen.getAllByRole("row")[1];
    expect(within(fila).getByText("ana@negocio.pe")).toBeTruthy();
    expect(within(fila).getByText("15 min")).toBeTruthy();
    expect(fila.textContent).toContain("10:00");
  });

  it("conserva el orden que manda el backend, lo más reciente primero", () => {
    render(
      <AccesosDeSoporte
        accesos={[
          { ocurrido_en: "2026-10-04T15:00:00Z", usuario: "b@x.pe", duracion_segundos: 900 },
          { ocurrido_en: "2026-10-01T15:00:00Z", usuario: "a@x.pe", duracion_segundos: 900 },
        ]}
      />,
    );

    const filas = screen.getAllByRole("row").slice(1);
    expect(filas[0].textContent).toContain("b@x.pe");
    expect(filas[1].textContent).toContain("a@x.pe");
  });

  /** El cliente tiene derecho a ver que hubo un acceso aunque el registro no se entienda: no se esconde, se muestra con su fecha. */
  it("un acceso sin detalle se muestra igual, con su fecha y «sin detalle»", () => {
    render(<AccesosDeSoporte accesos={[{ ocurrido_en: "2026-10-04T15:00:00Z" }]} />);

    const fila = screen.getAllByRole("row")[1];
    expect(fila.textContent).toContain("10:00");
    expect(within(fila).getAllByText("Sin detalle")).toHaveLength(2);
  });

  it("sin accesos lo dice, en vez de una tabla vacía", () => {
    render(<AccesosDeSoporte accesos={[]} />);

    expect(screen.getByText("Nadie del equipo de soporte ha entrado a tu cuenta.")).toBeTruthy();
    expect(screen.getAllByRole("row")).toHaveLength(2);
  });

  it("las columnas se llaman cuándo, usuario y duración máxima", () => {
    render(<AccesosDeSoporte accesos={[]} />);

    expect(screen.getByRole("columnheader", { name: "Cuándo" })).toBeTruthy();
    expect(screen.getByRole("columnheader", { name: "Usuario" })).toBeTruthy();
    expect(screen.getByRole("columnheader", { name: "Duración máxima" })).toBeTruthy();
  });

  /** Nada del administrador: ni columna ni texto. */
  it("no tiene ninguna columna que hable del administrador", () => {
    render(<AccesosDeSoporte accesos={[]} />);

    expect(screen.queryByRole("columnheader", { name: /administrador|soporte|quién/i })).toBeNull();
  });
});
