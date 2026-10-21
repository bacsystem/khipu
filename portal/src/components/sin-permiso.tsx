import { LockIcon } from "lucide-react";
import { ApiError } from "@/lib/api/types";
import { useMessages } from "next-intl";
import { TARJETA } from "@/lib/estilos";
import { cn } from "@/lib/utils";

/** El backend respondió 403 ROL_INSUFICIENTE (#21): la página es de administradores y el usuario tiene otro rol. */
export function esRolInsuficiente(error: unknown): boolean {
  return error instanceof ApiError && error.status === 403 && error.codigo === "ROL_INSUFICIENTE";
}

/** Lo que ve quien llega a una página de administradores con otro rol (p. ej. desde un enlace guardado): se explica en vez de fallar. */
export function SinPermisoDePagina() {
  const messages = useMessages();
  return (
    <div className={cn(TARJETA, "mx-auto flex w-full max-w-xl items-start gap-3 p-5")} role="status">
      <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
        <LockIcon className="size-4" />
      </div>
      <p className="text-sm text-muted-foreground">{messages.roles.sinPermiso.pagina}</p>
    </div>
  );
}
