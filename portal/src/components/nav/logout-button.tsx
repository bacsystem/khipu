"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { messages } from "@/lib/messages";

export function LogoutButton() {
  const router = useRouter();
  const [saliendo, setSaliendo] = useState(false);

  async function salir() {
    setSaliendo(true);
    await fetch("/api/auth/logout", { method: "POST" });
    router.push("/login");
    router.refresh();
  }

  return (
    <Button variant="ghost" size="sm" disabled={saliendo} onClick={salir} className="justify-start text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-foreground">
      {messages.comun.cerrarSesion}
    </Button>
  );
}
