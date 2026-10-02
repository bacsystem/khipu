"use client";

import { Menu } from "lucide-react";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetTrigger } from "@/components/ui/sheet";
import type { Administrador } from "@/lib/api/admin-auth";
import { messages } from "@/lib/messages";
import { AdminSidebar } from "./admin-sidebar";

/** El sidebar del backoffice solo se ve desde `md`; en móvil esta es la única navegación. Se cierra al cambiar de página. */
export function AdminMobileNav({ administrador }: { administrador: Administrador }) {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();

  useEffect(() => {
    setOpen(false);
  }, [pathname]);

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger render={<Button variant="ghost" size="icon-sm" />}>
        <Menu className="size-5" />
        <span className="sr-only">{messages.admin.topbar.abrirMenu}</span>
      </SheetTrigger>
      <SheetContent side="left" showCloseButton={false} className="w-60 bg-sidebar p-0 text-sidebar-foreground">
        <AdminSidebar administrador={administrador} />
      </SheetContent>
    </Sheet>
  );
}
