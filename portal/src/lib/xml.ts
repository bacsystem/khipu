// Indenta XML "en una sola línea" para mostrarlo legible. No valida ni reordena nada.
export function formatearXml(xml: string): string {
  const compacto = xml.replace(/>\s+</g, "><").trim();
  const partes = compacto.split(/(?=<)|(?<=>)/).filter((p) => p.trim() !== "");
  const lineas: string[] = [];
  let nivel = 0;
  for (const parte of partes) {
    const esCierre = /^<\//.test(parte);
    const esAutocerrado = /\/>$/.test(parte) || /^<\?/.test(parte) || /^<!/.test(parte);
    const esApertura = /^<[^/?!]/.test(parte) && !esAutocerrado;
    if (esCierre) nivel = Math.max(0, nivel - 1);
    if (!parte.startsWith("<") && lineas.length > 0) {
      // texto: lo pegamos a la etiqueta de apertura anterior
      lineas[lineas.length - 1] += parte;
      continue;
    }
    if (esCierre && lineas.length > 0 && !lineas[lineas.length - 1].trimEnd().endsWith(">")) {
      lineas[lineas.length - 1] += parte;
      continue;
    }
    lineas.push("  ".repeat(nivel) + parte);
    if (esApertura) nivel++;
  }
  return lineas.join("\n");
}
