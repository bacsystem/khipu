import { describe, expect, it } from "vitest";
import { ipDelCliente, saltosDeConfianza } from "./ip-cliente";

describe("ipDelCliente", () => {
  describe("sin proxies de confianza no se cree a nadie", () => {
    it("con 0 saltos no hay IP aunque llegue la cabecera: cualquiera puede escribirla", () => {
      expect(ipDelCliente("203.0.113.7", 0)).toBeUndefined();
      expect(ipDelCliente("6.6.6.6, 203.0.113.7", 0)).toBeUndefined();
    });

    it("saltos negativos o no enteros cuentan como 0", () => {
      expect(ipDelCliente("203.0.113.7", -1)).toBeUndefined();
      expect(ipDelCliente("203.0.113.7", Number.NaN)).toBeUndefined();
      expect(ipDelCliente("203.0.113.7", 1.5)).toBeUndefined();
    });
  });

  describe("se cuenta desde la derecha, que es donde escriben los proxies de confianza", () => {
    it("con 1 salto, la última entrada", () => {
      expect(ipDelCliente("203.0.113.7", 1)).toBe("203.0.113.7");
      expect(ipDelCliente("203.0.113.7, 100.64.0.5", 1)).toBe("100.64.0.5");
    });

    it("lo que el navegador antepone a la izquierda nunca gana", () => {
      expect(ipDelCliente("6.6.6.6, 203.0.113.7", 1)).toBe("203.0.113.7");
      expect(ipDelCliente("6.6.6.6, 7.7.7.7, 203.0.113.7", 1)).toBe("203.0.113.7");
    });

    it("con 2 saltos (borde y red interna), la penúltima", () => {
      expect(ipDelCliente("203.0.113.7, 100.64.0.5", 2)).toBe("203.0.113.7");
      expect(ipDelCliente("6.6.6.6, 203.0.113.7, 100.64.0.5", 2)).toBe("203.0.113.7");
    });

    it("si la cadena es más corta que los saltos configurados no se adivina: sin IP", () => {
      expect(ipDelCliente("203.0.113.7", 2)).toBeUndefined();
    });

    it("tolera espacios alrededor de las comas", () => {
      expect(ipDelCliente("  6.6.6.6 ,   203.0.113.7  ", 1)).toBe("203.0.113.7");
    });
  });

  describe("normaliza lo que escriben los proxies", () => {
    it("quita el puerto de una IPv4", () => {
      expect(ipDelCliente("203.0.113.7:51234", 1)).toBe("203.0.113.7");
    });

    it("acepta IPv6, con corchetes y puerto, y la deja en su forma canónica", () => {
      expect(ipDelCliente("2001:db8::1", 1)).toBe("2001:db8::1");
      expect(ipDelCliente("[2001:db8::1]:443", 1)).toBe("2001:db8::1");
      expect(ipDelCliente("[2001:DB8:0:0:0:0:0:1]", 1)).toBe("2001:db8::1");
    });

    it("una IPv4 mapeada en IPv6 es la IPv4", () => {
      expect(ipDelCliente("::ffff:203.0.113.7", 1)).toBe("203.0.113.7");
    });
  });

  describe("lo que no es una IP no sale hacia el backend", () => {
    it.each([
      ["vacío", ""],
      ["unknown", "unknown"],
      ["texto", "no-es-una-ip"],
      ["octeto fuera de rango", "999.1.1.1"],
      ["incompleta", "203.0.113"],
      ["IPv6 rota", "2001:db8::zz"],
      ["inyección de cabecera", "1.2.3.4\r\nX-Platform-Key: robada"],
      ["con ruta", "203.0.113.7/24"],
    ])("%s", (_nombre, cadena) => {
      expect(ipDelCliente(cadena, 1)).toBeUndefined();
    });

    it("sin cabecera no hay IP", () => {
      expect(ipDelCliente(null, 1)).toBeUndefined();
      expect(ipDelCliente(undefined, 1)).toBeUndefined();
    });

    it("una entrada vacía entre las que escribieron los proxies de confianza invalida la lectura", () => {
      // Con 2 saltos, la cadena termina en `…, borde, interno`. Si el último está vacío, contar desde la derecha
      // devolvería la IP del proxy interno como si fuera la del cliente.
      expect(ipDelCliente("203.0.113.7,100.64.0.5,", 2)).toBeUndefined();
      expect(ipDelCliente("203.0.113.7,,100.64.0.5", 3)).toBeUndefined();
    });

    it("lo vacío o basura a la izquierda no importa: es lo que controla el navegador y nunca se mira", () => {
      // Si bastara un `,` a la izquierda para anular la lectura, cualquiera podría borrar su propia IP de la bitácora.
      expect(ipDelCliente(",6.6.6.6,203.0.113.7", 1)).toBe("203.0.113.7");
      expect(ipDelCliente(", , ,203.0.113.7", 1)).toBe("203.0.113.7");
      expect(ipDelCliente("basura, ,203.0.113.7, 100.64.0.5", 2)).toBe("203.0.113.7");
    });
  });
});

describe("saltosDeConfianza", () => {
  it("por defecto 0: sin configurar, no se cree en ningún proxy", () => {
    expect(saltosDeConfianza(undefined)).toBe(0);
    expect(saltosDeConfianza("")).toBe(0);
  });

  it("lee un entero no negativo", () => {
    expect(saltosDeConfianza("1")).toBe(1);
    expect(saltosDeConfianza(" 2 ")).toBe(2);
  });

  it("un valor mal escrito vale 0, no «lo más parecido»", () => {
    for (const malo of ["abc", "-1", "1.5", "1e2", "uno"]) expect(saltosDeConfianza(malo), malo).toBe(0);
  });
});
