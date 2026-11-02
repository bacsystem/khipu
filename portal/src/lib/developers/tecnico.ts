/**
 * Marca un texto técnico de la documentación para developers (#25): un ejemplo de request o de respuesta, un snippet de código o el CSS de
 * Scalar. Son los mismos en cualquier idioma —los nombres de campos y los datos de ejemplo son los de la API— y por eso no van a `messages`.
 * Es un template literal etiquetado que devuelve el texto tal cual (con los escapes ya resueltos, como uno sin etiqueta); la etiqueta es lo
 * que le dice a la regla `khipu/no-literal-de-ui` que no es un texto de la UI.
 */
export function tecnico(partes: TemplateStringsArray, ...valores: unknown[]): string {
  return partes.reduce((texto, parte, i) => texto + (i > 0 ? String(valores[i - 1]) : "") + parte, "");
}
