import type { NextRequest } from "next/server";
import { activarPlan } from "@/lib/api/admin-planes";
import { sobrePlan } from "../../comun";

/** Volver a ofrecer un plan desactivado (#190). */
export function POST(req: NextRequest, ctx: { params: Promise<{ id: string }> }) {
  return sobrePlan(req, ctx, activarPlan);
}
