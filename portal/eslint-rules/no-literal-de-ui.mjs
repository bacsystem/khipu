/**
 * `khipu/no-literal-de-ui` (#25): un texto que ve el usuario no se escribe en el componente, se lee de `src/messages/<locale>.json`.
 *
 * Marca tres cosas:
 *  1. Texto JSX con alguna letra (`<p>Hola</p>`).
 *  2. Un string con forma de texto humano dentro de JSX: el valor de un atributo (`placeholder="Buscar"`, `titulo="Planes"`)
 *     o una expresión hija (`{vacio ? "Sin datos" : null}`).
 *  3. Fuera de JSX, un string con forma de frase en castellano (`toast("Guardado.")`, `{ label: "Fecha de emisión" }`).
 *
 * «Forma de texto humano» es una heurística, no un análisis: tiene una letra y además empieza en mayúscula seguida de minúscula,
 * lleva tilde/ñ/¿/¡, o tiene un espacio entre palabras. Lo técnico no cuenta: clases de Tailwind, rutas, URLs, códigos en
 * MAYÚSCULAS, identificadores, `data-testid`, comparaciones, `case`, claves de objeto, `import`, `console.*` y `new Error()`.
 *
 * Los textos que ya existen viven en la línea base `eslint-suppressions.json` (supresiones masivas de ESLint), que solo puede bajar:
 * un literal nuevo falla `npm run lint`, y migrar uno sin podar la línea base también (ver portal/README.md, «Textos e idiomas»).
 */

const ATRIBUTOS_TECNICOS = new Set([
  "className",
  "class",
  "href",
  "src",
  "srcSet",
  "id",
  "key",
  "type",
  "name",
  "role",
  "variant",
  "size",
  "autoComplete",
  "inputMode",
  "method",
  "action",
  "target",
  "rel",
  "htmlFor",
  "as",
  "mode",
  "side",
  "align",
  "form",
  "pattern",
  "accept",
  "lang",
  "dir",
  "style",
  "sizes",
  "fill",
  "stroke",
  "viewBox",
  "d",
  "points",
  "transform",
  "strokeLinecap",
  "strokeLinejoin",
  "fontFamily",
  "textAnchor",
  "dominantBaseline",
  "ruta",
  "href",
  "tono",
  "icono",
  "estado",
  "color",
  "nativeButton",
  "orientation",
  "defaultValue",
  "value",
  "aria-controls",
  "aria-describedby",
  "aria-labelledby",
  "aria-live",
  "aria-current",
  "aria-haspopup",
  "aria-hidden",
  "aria-expanded",
  "aria-invalid",
  "aria-sort",
  "aria-orientation",
  "aria-autocomplete",
]);

const LETRA = /\p{L}/u;
const CASTELLANO = /[áéíóúñüÁÉÍÓÚÑÜ¿¡«»…]/u;
const ESPACIO_ENTRE_PALABRAS = /\p{L}\s+\p{L}/u;
const MAYUSCULA_Y_MINUSCULA = /^\p{Lu}\p{Ll}/u;
const TECNICO = [
  /^[A-Z0-9_.\-]+$/, // códigos y constantes: PEN, FACTURA_01, X-Empresa
  /^(https?:|mailto:|tel:|\/|\.\/|#)/, // rutas y URLs
  /^[\w.+-]+@[\w-]+\.[\w.]+$/, // correos de ejemplo
  /^[a-z][\w-]*$/, // identificadores en minúscula: default, outline, email
  /^(Bearer|Basic)\s*$/, // esquemas de Authorization
  /^[A-Z][A-Za-z]+\/[A-Za-z_]+$/, // zonas horarias: America/Lima
  /^[A-Z][a-z]+(-[A-Z][a-z]+)+$/, // cabeceras HTTP: Idempotency-Key
  /^[A-Z][a-z]+[A-Z]\w*$/, // identificadores PascalCase: ApiError
];

/** ¿Tiene forma de texto que lee una persona? */
export function pareceTextoDeUi(valor, { estricto }) {
  const s = valor.trim();
  if (!s || !LETRA.test(s)) return false;
  if (TECNICO.some((re) => re.test(s))) return false;
  if (CASTELLANO.test(s)) return true;
  if (MAYUSCULA_Y_MINUSCULA.test(s)) return true;
  // Fuera de JSX un espacio no basta: `"flex items-center gap-2"` también lo tiene. Ahí se exige mayúscula inicial o tilde.
  if (estricto) return false;
  // Dentro de JSX, varias palabras en minúscula sin guiones ni dos puntos de Tailwind también son texto: «o», «de 10».
  return ESPACIO_ENTRE_PALABRAS.test(s) && !/[:/[\]]|(^|\s)-?[a-z]+-[\w-]+/.test(s);
}

function nombreDeAtributo(attr) {
  if (attr.name.type === "JSXNamespacedName") return `${attr.name.namespace.name}:${attr.name.name.name}`;
  return attr.name.name;
}

function esAtributoTecnico(nombre) {
  return ATRIBUTOS_TECNICOS.has(nombre) || nombre.startsWith("data-") || /className$|Class$|Href$|Ruta$|Id$|Variant$|Tono$/.test(nombre);
}

/** El callee de una llamada como texto: `console.error`, `cn`, `t.raw`. */
function nombreDeLlamada(callee) {
  if (callee.type === "Identifier") return callee.name;
  if (callee.type === "MemberExpression" && !callee.computed) {
    const obj = callee.object.type === "Identifier" ? callee.object.name : callee.object.type === "ThisExpression" ? "this" : "?";
    return `${obj}.${callee.property.name}`;
  }
  return "";
}

const LLAMADAS_TECNICAS = /^(cn|cva|clsx|twMerge|require|console\.\w+|Error|TypeError|RangeError|t|t\.raw|t\.rich|t\.markup|useTranslations|getTranslations|fetch|Symbol|describe|it|test|expect|vi\.\w+|redirect|notFound|encodeURIComponent|new URL|URL|URLSearchParams|headers\.(get|set)|cookies\.\w+|\w+\.(get|set|has|delete|append|startsWith|endsWith|includes|split|replace|replaceAll|padStart|match|test|setAttribute|getAttribute|querySelector|querySelectorAll|localeCompare|toLocaleString|toLocaleDateString)|searchParams\.\w+)$/;

/** ¿El string está en un sitio donde no es texto de UI aunque lo parezca? */
function esContextoTecnico(node) {
  const padre = node.parent;
  if (!padre) return true;
  switch (padre.type) {
    case "ImportDeclaration":
    case "ExportNamedDeclaration":
    case "ExportAllDeclaration":
    case "ImportExpression":
    case "TSLiteralType":
    case "TSEnumMember":
    case "TSExternalModuleReference":
    case "TSImportType":
    case "SwitchCase":
      return true;
    case "ExpressionStatement":
      return true; // directivas: "use client", "use server"
    case "Property":
      return padre.key === node; // la clave de un objeto
    case "BinaryExpression":
      return ["===", "!==", "==", "!=", "in", "instanceof"].includes(padre.operator);
    case "MemberExpression":
      return padre.property === node; // obj["clave"]
    case "NewExpression":
    case "CallExpression": {
      const nombre = padre.type === "NewExpression" ? `new ${nombreDeLlamada(padre.callee)}` : nombreDeLlamada(padre.callee);
      return LLAMADAS_TECNICAS.test(nombre) || /^new (Error|TypeError|RangeError|URL|URLSearchParams|RegExp|Intl\.\w+)$/.test(nombre) || nombre === "Error";
    }
    case "ThrowStatement":
      return true;
    default:
      return false;
  }
}

/** Recorre una expresión dentro de JSX buscando strings que se van a pintar: ternarios, `&&`, `??`, concatenaciones y template literals. */
function stringsQueSePintan(expr, encontrados) {
  if (!expr) return;
  switch (expr.type) {
    case "Literal":
      if (typeof expr.value === "string") encontrados.push({ node: expr, valor: expr.value });
      return;
    case "TemplateLiteral":
      for (const q of expr.quasis) encontrados.push({ node: q, valor: q.value.cooked ?? q.value.raw });
      return;
    case "ConditionalExpression":
      stringsQueSePintan(expr.consequent, encontrados);
      stringsQueSePintan(expr.alternate, encontrados);
      return;
    case "LogicalExpression":
      stringsQueSePintan(expr.right, encontrados);
      if (expr.operator !== "&&") stringsQueSePintan(expr.left, encontrados);
      return;
    case "BinaryExpression":
      if (expr.operator === "+") {
        stringsQueSePintan(expr.left, encontrados);
        stringsQueSePintan(expr.right, encontrados);
      }
      return;
    default:
      return;
  }
}

const regla = {
  meta: {
    type: "suggestion",
    docs: { description: "Los textos de la UI viven en src/messages/<locale>.json, no en el componente (#25)." },
    schema: [],
    messages: {
      texto: "Texto de UI escrito en el componente: «{{texto}}». Muévelo a src/messages/es.json (y en.json) y léelo con useMessages()/getMessages() (portal/README.md, «Textos e idiomas»).",
    },
  },
  create(context) {
    const reportados = new WeakSet();
    const reportar = (node, valor) => {
      if (reportados.has(node)) return;
      reportados.add(node);
      const texto = valor.trim().replace(/\s+/g, " ");
      context.report({ node, messageId: "texto", data: { texto: texto.length > 60 ? `${texto.slice(0, 57)}…` : texto } });
    };
    const revisarEnJsx = (expr) => {
      const encontrados = [];
      stringsQueSePintan(expr, encontrados);
      for (const { node, valor } of encontrados) if (pareceTextoDeUi(valor, { estricto: false })) reportar(node, valor);
    };
    const dentroDeJsx = new WeakSet();

    return {
      JSXText(node) {
        if (LETRA.test(node.value)) reportar(node, node.value);
      },
      JSXAttribute(node) {
        const nombre = nombreDeAtributo(node);
        if (!node.value || esAtributoTecnico(nombre)) {
          if (node.value) dentroDeJsx.add(node.value);
          return;
        }
        if (node.value.type === "Literal") {
          dentroDeJsx.add(node.value);
          if (typeof node.value.value === "string" && pareceTextoDeUi(node.value.value, { estricto: false })) reportar(node.value, node.value.value);
        } else if (node.value.type === "JSXExpressionContainer") {
          revisarEnJsx(node.value.expression);
        }
      },
      JSXExpressionContainer(node) {
        if (node.parent?.type === "JSXAttribute") return;
        revisarEnJsx(node.expression);
      },
      Literal(node) {
        if (typeof node.value !== "string" || reportados.has(node) || dentroDeJsx.has(node)) return;
        if (node.parent?.type?.startsWith("JSX")) return;
        if (esContextoTecnico(node)) return;
        if (pareceTextoDeUi(node.value, { estricto: true })) reportar(node, node.value);
      },
      TemplateLiteral(node) {
        if (node.parent?.type === "JSXExpressionContainer" || node.parent?.type === "TaggedTemplateExpression") return;
        if (esContextoTecnico(node)) return;
        for (const q of node.quasis) {
          const valor = q.value.cooked ?? q.value.raw;
          if (!reportados.has(q) && pareceTextoDeUi(valor, { estricto: true })) reportar(q, valor);
        }
      },
    };
  },
};

export default regla;
