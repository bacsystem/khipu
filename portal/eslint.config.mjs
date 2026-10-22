import { dirname } from "path";
import { fileURLToPath } from "url";
import { FlatCompat } from "@eslint/eslintrc";
import noLiteralDeUi from "./eslint-rules/no-literal-de-ui.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const compat = new FlatCompat({
  baseDirectory: __dirname,
});

const eslintConfig = [
  ...compat.extends("next/core-web-vitals", "next/typescript"),
  {
    ignores: [
      "node_modules/**",
      // `.next-*` cubre los distDir alternativos: `.next-prod` de `make build-portal`, `.next-e2e` y `.next-mock`.
      // Sin esto, un build deja 900 errores de lint en código generado y `make verificar` deja de significar algo.
      ".next/**",
      ".next-*/**",
      "out/**",
      "build/**",
      "next-env.d.ts",
    ],
  },
  {
    // #25: ningún texto de UI escrito en el código; vive en src/messages/<locale>.json. Lo que había antes está en la línea base
    // eslint-suppressions.json, que solo puede bajar (ver README, «Textos e idiomas»).
    files: ["src/app/**/*.{ts,tsx}", "src/components/**/*.{ts,tsx}", "src/lib/**/*.{ts,tsx}"],
    ignores: [
      "**/*.test.{ts,tsx}",
      "**/*.d.ts",
      // Los componentes del design system llegan del paquete khipu-design-system (#16), fuera de src/: no los cubre esta regla.
      // Rutas del BFF: sus strings son códigos de error y cabeceras para la API, no texto que se pinta.
      "src/app/api/**",
    ],
    plugins: { khipu: { rules: { "no-literal-de-ui": noLiteralDeUi } } },
    rules: { "khipu/no-literal-de-ui": "error" },
  },
  {
    // #25: los textos llegan por useMessages()/getMessages() o por parámetro. Importar el JSON desde código que puede ir al
    // navegador mete el catálogo entero (cientos de KB) en un chunk de cliente, y en castellano fijo.
    files: ["src/app/**/*.{ts,tsx}", "src/components/**/*.{ts,tsx}", "src/lib/**/*.{ts,tsx}"],
    ignores: ["**/*.test.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["@/messages/*.json", "**/messages/*.json"],
              message: "Lee los textos con useMessages()/getMessages() o recíbelos por parámetro (src/i18n/mensajes.ts).",
            },
          ],
        },
      ],
    },
  },
];

export default eslintConfig;
