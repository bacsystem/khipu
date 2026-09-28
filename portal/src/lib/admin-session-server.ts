import { cookies } from "next/headers";
import { COOKIE_ADMIN_ACCESS } from "./admin-session";

export async function getAdminServerSession() {
  const store = await cookies();
  return { access: store.get(COOKIE_ADMIN_ACCESS)?.value };
}
