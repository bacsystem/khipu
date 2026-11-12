import { NextRequest, NextResponse } from "next/server";
import { restablecer } from "@/lib/api/auth";
import { leerCuerpoJson } from "@/lib/api/cuerpo-json";
import { errorResponse } from "@/lib/api/http";

export async function POST(req: NextRequest) {
  const leido = await leerCuerpoJson<{ token: string; password: string }>(req);
  if ("error" in leido) return leido.error;
  const { token, password } = leido.cuerpo;
  try {
    await restablecer(token, password);
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    return errorResponse(err);
  }
}
