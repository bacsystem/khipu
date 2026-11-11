import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import es from "@/messages/es.json";
import { Planes } from "./planes";
import { planesDeRespaldo } from "./planes-datos";

afterEach(cleanup);

const tarjeta = (nombre: string) => screen.getByRole("heading", { level: 3, name: nombre }).closest("[data-slot=card]") as HTMLElement;

/** #580: el precio de la portada, con el IGV en su línea, y lo que cada plan dice de Producción y Beta. */
/** #581: cada plan de pago lleva su nombre al registro; el gratis no pide nada. Con el registro cerrado, todos van al contacto. */
describe("Planes de la portada: a dónde lleva cada botón", () => {
  it("los de pago llevan su plan al registro y el gratis va al registro a secas", () => {
    render(<Planes planes={planesDeRespaldo(es)} abierto contacto={null} />);

    expect(screen.getByRole("link", { name: "Empezar gratis" }).getAttribute("href")).toBe("/registro");
    expect(screen.getByRole("link", { name: "Elegir Emprende" }).getAttribute("href")).toBe("/registro?plan=emprende");
    expect(screen.getByRole("link", { name: "Elegir Negocio" }).getAttribute("href")).toBe("/registro?plan=negocio");
    expect(screen.getByRole("link", { name: "Elegir Pro" }).getAttribute("href")).toBe("/registro?plan=pro");
  });

  it("con el registro cerrado todos van al contacto", () => {
    render(<Planes planes={planesDeRespaldo(es)} abierto={false} contacto="https://wa.me/51987654321" />);

    for (const enlace of screen.getAllByRole("link", { name: "Pedir acceso" })) expect(enlace.getAttribute("href")).toBe("https://wa.me/51987654321");
  });
});

describe("Planes de la portada", () => {
  it("el monto y su sufijo no se parten, y el IGV va en su propia línea solo en los planes de pago", () => {
    render(<Planes planes={planesDeRespaldo(es)} abierto contacto={null} />);

    const emprende = tarjeta("Emprende");
    const precio = within(emprende).getByTestId("precio-plan");
    expect(precio.textContent?.trim()).toBe("S/ 29");
    expect(precio.className).toContain("whitespace-nowrap");
    expect(within(emprende).getByText("/mes").className).toContain("whitespace-nowrap");
    expect(within(emprende).getByText("IGV incluido")).toBeTruthy();

    expect(within(tarjeta("Gratis")).queryByText("IGV incluido")).toBeNull();
    expect(within(tarjeta("Gratis")).getByText("sin tarjeta")).toBeTruthy();
  });

  it("al año dice el total una sola vez con el IGV, sin repetirlo", () => {
    render(<Planes planes={planesDeRespaldo(es)} abierto contacto={null} />);
    fireEvent.click(screen.getByRole("button", { name: /Anual/ }));

    const negocio = tarjeta("Negocio");
    expect(within(negocio).getByText("S/ 690 al año · 2 meses libres")).toBeTruthy();
    expect(within(negocio).getAllByText(/IGV/)).toHaveLength(1);
  });

  /** #613: Gratis da 10 comprobantes reales en total (no al mes), y cada plan 1 000 pruebas en Beta al mes por cada RUC: ya no hay Beta «sin límite». */
  it("Gratis da 10 comprobantes reales en total y cada plan sus pruebas en Beta por RUC; nadie vende «Producción además de pruebas»", () => {
    render(<Planes planes={planesDeRespaldo(es)} abierto contacto={null} />);

    const gratis = tarjeta("Gratis");
    expect(within(gratis).getByText("10 comprobantes reales en total")).toBeTruthy();
    expect(within(gratis).getByText("Comprobantes reales en Producción")).toBeTruthy();
    expect(within(gratis).getByText("1 000 pruebas en Beta al mes")).toBeTruthy();
    expect(within(tarjeta("Emprende")).getByText("1 000 pruebas en Beta al mes")).toBeTruthy();
    expect(within(tarjeta("Negocio")).getByText("3 000 pruebas en Beta al mes")).toBeTruthy();
    expect(within(tarjeta("Pro")).getByText("10 000 pruebas en Beta al mes")).toBeTruthy();
    expect(within(tarjeta("Pro")).getByText("10 000 documentos/mes")).toBeTruthy();
    expect(screen.queryByText(/sin límite/)).toBeNull();
    expect(screen.queryByText(/Producción además de pruebas|Entorno de pruebas de SUNAT/)).toBeNull();
    expect(screen.getByText(/Las pruebas en Beta van aparte: 1 000 al mes por cada RUC de tu plan/)).toBeTruthy();
  });
});
