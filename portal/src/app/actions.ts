"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { baseCookie, COOKIE_EMPRESA, REFRESH_MAX_AGE } from "@/lib/session";

export async function seleccionarEmpresa(formData: FormData) {
  const empresaId = String(formData.get("empresaId") ?? "");
  const store = await cookies();
  store.set(COOKIE_EMPRESA, empresaId, { ...baseCookie, maxAge: REFRESH_MAX_AGE });
  redirect("/comprobantes");
}
