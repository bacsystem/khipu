/**
 * #358: el redondeo del importe total (`PayableRoundingAmount`) lo valida el dominio igual (3303): hasta 2 decimales y entre −1.00 y 1.00. El formulario lo pide como
 * texto para dejar escribir «-» o «0.» sin reescribirlo; esto dice qué número es y, si no vale, por qué. Vacío, o 0, es «sin redondeo».
 */
export function redondeoDe(texto: string): { valor: number | null; error: string | null } {
  const t = texto.trim();
  if (t === "") return { valor: null, error: null };
  if (!/^-?\d+(\.\d{1,2})?$|^-?\.\d{1,2}$/.test(t)) return { valor: null, error: "El redondeo es un importe con hasta 2 decimales, por ejemplo -0.05 o 0.02." };
  const valor = Number(t);
  if (Math.abs(valor) > 1) return { valor: null, error: "El redondeo no puede superar 1.00 en valor absoluto (3303)." };
  return { valor: valor === 0 ? null : valor, error: null };
}
