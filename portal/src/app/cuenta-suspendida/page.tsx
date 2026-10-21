import { AuthShell } from "@/components/auth/auth-shell";
import { CuentaSuspendida } from "@/components/auth/cuenta-suspendida";
import type { Metadata } from "next";
import { useMessages } from "next-intl";
import { getMessages } from "next-intl/server";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.cuentaSuspendida.titulo} · ${messages.app.nombre}` };
}

/**
 * Pública, y a propósito fuera de `(privado)`: la sesión de una cuenta suspendida sigue existiendo pero el backend le niega todo, así que una
 * página privada volvería a fallar. Acá solo se explica y se deja cerrar la sesión (#182).
 */
export default function CuentaSuspendidaPage() {
  const messages = useMessages();
  return (
    <AuthShell title={messages.cuentaSuspendida.titulo} subtitle={messages.app.nombre}>
      <CuentaSuspendida />
    </AuthShell>
  );
}
