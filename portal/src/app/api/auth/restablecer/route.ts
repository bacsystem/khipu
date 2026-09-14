import { NextRequest, NextResponse } from "next/server";
import { restablecer } from "@/lib/api/auth";
import { errorResponse } from "@/lib/api/http";

export async function POST(req: NextRequest) {
  const { token, password } = await req.json();
  try {
    await restablecer(token, password);
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    return errorResponse(err);
  }
}
