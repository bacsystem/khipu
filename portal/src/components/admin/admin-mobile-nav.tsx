"use client";

import { ListIcon } from "@phosphor-icons/react";
import { useMessages } from "next-intl";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { Button } from "khipu-design-system/ui/button";
import { Sheet, SheetContent, SheetTrigger } from "khipu-design-system/ui/sheet";
import type { Administrador } from "@/lib/api/admin-auth";
import { AdminSidebar } from "./admin-sidebar";

/** El sidebar del backoffice solo se ve desde `md`; en móvil esta es la única navegación. Se cierra al cambiar de página. */
export function AdminMobileNav({ administrador }: { administrador: Administrador }) {
  const messages = useMessages();
  const [open, setOpen] = useState(false);
  const pathname = usePathname();

  useEffect(() => {
    setOpen(false);
  }, [pathname]);

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger render={<Button variant="ghost" size="icon-sm" />}>
        <ListIcon weight="bold" className="size-5" />
        <span className="sr-only">{messages.admin.topbar.abrirMenu}</span>
      </SheetTrigger>
      <SheetContent side="left" showCloseButton={false} className="w-60 bg-sidebar p-0 text-sidebar-foreground">
        {/* El cajón no se desplaza por sí solo (su contenido es `h-full overflow-hidden`): sin este contenedor el menú de usuario queda fuera en una pantalla baja. */}
        <div className="h-full overflow-y-auto [&>div]:h-auto [&>div]:min-h-full [&>div]:overflow-visible">
          <AdminSidebar administrador={administrador} />
        </div>
      </SheetContent>
    </Sheet>
  );
}
