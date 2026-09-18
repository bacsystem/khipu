import type { ReactNode } from "react";
import { DevelopersHeader } from "@/components/developers/developers-header";
import { getServerSession } from "@/lib/session-server";

export default async function DevelopersLayout({ children }: { children: ReactNode }) {
  const { access, empresaId } = await getServerSession();
  return (
    <div className="min-h-screen bg-background">
      <DevelopersHeader autenticado={Boolean(access && empresaId)} />
      {children}
    </div>
  );
}
