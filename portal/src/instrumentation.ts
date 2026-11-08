export async function register() {
  if (process.env.NEXT_RUNTIME === "nodejs") {
    // #250: un SUPPORT_URL o SUPPORT_EMAIL mal formado se denuncia al arrancar, nombrando la variable, y las páginas no muestran ayuda hasta corregirlo.
    // Sin lanzar (272-H1): en producción, un error en register() cierra el proceso, y una variable opcional no puede tumbar el portal.
    const { soporteParaMostrar } = await import("./lib/soporte");
    soporteParaMostrar();
  }
  if (process.env.NEXT_RUNTIME === "nodejs" && process.env.API_MOCKING === "enabled") {
    const { server } = await import("./mocks/node");
    // "warn", no "bypass": una petición sin handler igual sigue su camino (y muere en el puerto muerto de
    // playwright.config), pero así el log del webServer nombra el endpoint que falta en vez de un ECONNREFUSED mudo.
    server.listen({ onUnhandledRequest: "warn" });
  }
  if (process.env.NEXT_RUNTIME === "nodejs") {
    // Fuga de memoria bajo carga: los sockets keep-alive hacia el backend se quedaban con la petición que los abrió (ver fetch-fuera-de-la-peticion.ts).
    // Aquí, al arrancar y antes de que Next y React envuelvan fetch, para quedar como la capa más interna.
    const { aislarFetchDeLaPeticion } = await import("./lib/fetch-fuera-de-la-peticion");
    aislarFetchDeLaPeticion();
  }
}
