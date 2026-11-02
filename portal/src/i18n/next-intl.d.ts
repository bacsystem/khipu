import type es from "@/messages/es.json";
import type { Locale } from "./config";

// Con esto `t("clave")` se comprueba contra es.json al compilar: una clave que no existe no pasa `npm run typecheck`.
declare module "next-intl" {
  interface AppConfig {
    Locale: Locale;
    Messages: typeof es;
  }
}
