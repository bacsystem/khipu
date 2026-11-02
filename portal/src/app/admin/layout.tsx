import type { ReactNode } from "react";
import { TextosDeCliente } from "@/i18n/textos-de-cliente";

/** Todo el backoffice (su login incluido): los textos de `admin` solo viajan al navegador acá (H2, #25). */
export default function AdminLayout({ children }: { children: ReactNode }) {
  return <TextosDeCliente segmento="admin">{children}</TextosDeCliente>;
}
