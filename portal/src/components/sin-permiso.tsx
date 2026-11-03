import { LockIcon } from "@phosphor-icons/react/dist/ssr";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { ApiError } from "@/lib/api/types";
import { useMessages } from "next-intl";

/** El backend respondió 403 ROL_INSUFICIENTE (#21): la página es de administradores y el usuario tiene otro rol. */
export function esRolInsuficiente(error: unknown): boolean {
  return error instanceof ApiError && error.status === 403 && error.codigo === "ROL_INSUFICIENTE";
}

/** Lo que ve quien llega a una página de administradores con otro rol (p. ej. desde un enlace guardado): se explica en vez de fallar. */
export function SinPermisoDePagina() {
  const messages = useMessages();
  // Un aviso de la página, no un error: la `Alerta` informativa del DS (pone `role="status"`).
  return (
    <Alerta tono="info" icon={LockIcon} className="mx-auto w-full max-w-xl">
      {messages.roles.sinPermiso.pagina}
    </Alerta>
  );
}
