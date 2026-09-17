import { describe, expect, it } from "vitest";
import { formatearXml } from "./xml";

describe("formatearXml", () => {
  it("indenta etiquetas anidadas y mantiene el texto pegado a su etiqueta", () => {
    const xml = `<?xml version="1.0"?><Invoice><cbc:ID>F001-1</cbc:ID><cac:Party><cbc:Name>ACME</cbc:Name></cac:Party><cbc:Note/></Invoice>`;
    expect(formatearXml(xml)).toBe(
      [
        `<?xml version="1.0"?>`,
        `<Invoice>`,
        `  <cbc:ID>F001-1</cbc:ID>`,
        `  <cac:Party>`,
        `    <cbc:Name>ACME</cbc:Name>`,
        `  </cac:Party>`,
        `  <cbc:Note/>`,
        `</Invoice>`,
      ].join("\n"),
    );
  });

  it("no rompe XML ya formateado", () => {
    const xml = "<a>\n  <b>1</b>\n</a>";
    expect(formatearXml(xml)).toBe("<a>\n  <b>1</b>\n</a>");
  });
});
