import type { NextRequest } from "next/server";
import { desactivarPlan } from "@/lib/api/admin-planes";
import { sobrePlan } from "../../comun";

/** Sacar un plan de la oferta sin tocar a las cuentas que ya lo tienen (#190). */
export function POST(req: NextRequest, ctx: { params: Promise<{ id: string }> }) {
  return sobrePlan(req, ctx, desactivarPlan);
}
