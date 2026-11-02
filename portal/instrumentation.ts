export async function register() {
  if (process.env.NEXT_RUNTIME === "nodejs") {
    // #250: un SUPPORT_URL o SUPPORT_EMAIL mal formado se denuncia al arrancar, nombrando la variable, y las páginas no muestran ayuda hasta corregirlo.
    // Sin lanzar (272-H1): en producción, un error en register() cierra el proceso, y una variable opcional no puede tumbar el portal.
    const { soporteParaMostrar } = await import("./src/lib/soporte");
    soporteParaMostrar();
  }
  if (process.env.NEXT_RUNTIME === "nodejs" && process.env.API_MOCKING === "enabled") {
    const { server } = await import("./src/mocks/node");
    // "warn", no "bypass": una petición sin handler igual sigue su camino (y muere en el puerto muerto de
    // playwright.config), pero así el log del webServer nombra el endpoint que falta en vez de un ECONNREFUSED mudo.
    server.listen({ onUnhandledRequest: "warn" });
  }
}
