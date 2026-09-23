export async function register() {
  if (process.env.NEXT_RUNTIME === "nodejs" && process.env.API_MOCKING === "enabled") {
    const { server } = await import("./src/mocks/node");
    // "warn", no "bypass": una petición sin handler igual sigue su camino (y muere en el puerto muerto de
    // playwright.config), pero así el log del webServer nombra el endpoint que falta en vez de un ECONNREFUSED mudo.
    server.listen({ onUnhandledRequest: "warn" });
  }
}
