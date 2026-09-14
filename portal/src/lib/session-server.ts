import { cookies } from "next/headers";
import { COOKIE_ACCESS, COOKIE_EMPRESA, COOKIE_REFRESH } from "./session";

export async function getServerSession() {
  const store = await cookies();
  return {
    access: store.get(COOKIE_ACCESS)?.value,
    refresh: store.get(COOKIE_REFRESH)?.value,
    empresaId: store.get(COOKIE_EMPRESA)?.value,
  };
}
