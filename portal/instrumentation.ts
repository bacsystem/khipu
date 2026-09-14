export async function register() {
  if (process.env.NEXT_RUNTIME === "nodejs" && process.env.API_MOCKING === "enabled") {
    const { server } = await import("./src/mocks/node");
    server.listen({ onUnhandledRequest: "bypass" });
  }
}
