"use client";

import { Menu } from "lucide-react";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetTrigger } from "@/components/ui/sheet";
import type { Usuario } from "@/lib/api/auth";
import type { Empresa } from "@/lib/api/empresas";
import { SidebarContent } from "./sidebar-content";

export function MobileNav({
  usuario,
  empresas,
  activaId,
}: {
  usuario: Usuario;
  empresas: Empresa[];
  activaId?: string;
}) {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();

  useEffect(() => {
    setOpen(false);
  }, [pathname]);

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger render={<Button variant="ghost" size="icon-sm" />}>
        <Menu className="size-5" />
        <span className="sr-only">Abrir menú</span>
      </SheetTrigger>
      <SheetContent side="left" showCloseButton={false} className="bg-sidebar px-4 py-6 text-sidebar-foreground">
        <SidebarContent usuario={usuario} empresas={empresas} activaId={activaId} />
      </SheetContent>
    </Sheet>
  );
}
