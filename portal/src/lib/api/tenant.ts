export function tenantHeaders(access: string, empresaId: string): Record<string, string> {
  return { Authorization: `Bearer ${access}`, "X-Empresa": empresaId };
}
