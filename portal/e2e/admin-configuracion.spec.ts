import { expect, test, type Page } from "@playwright/test";
import { entrarComoAdmin, menuAdmin } from "./admin-sesion";
import { esperarHidratacion } from "./hidratacion";

// El mock (src/mocks/handlers.ts) guarda la configuración de la plataforma. Las specs de la corrida comparten esa memoria en paralelo, y dentro de esta spec los tests también
// corren en paralelo (`fullyParallel`), así que:
//   · el remitente y el aviso de mantenimiento son UNO solo para todo el mock: cada uno se prueba en una sola prueba, que lo deja como lo encontró;
//   · los seis correos son independientes y cada prueba que guarda usa el suyo (BIENVENIDA solo se lee, RECUPERACION_CLAVE se guarda y se restaura, y así).
// Que el backend valide, guarde, deje la bitácora y mande de verdad el texto editado lo prueban `ConfiguracionDeLaPlataformaE2ETest` y los tests de cada capa; acá se prueba la pantalla y su BFF.

test.beforeEach(async ({ page }) => {
  await entrarComoAdmin(page);
});

const SECCION = { correo: "/admin/configuracion", plantillas: "/admin/configuracion?seccion=plantillas", aviso: "/admin/configuracion?seccion=aviso" };

async function abrir(page: Page, ruta: string, hidratado: string) {
  await page.goto(ruta);
  await esperarHidratacion(page, `[data-testid="${hidratado}"]`);
}

/** Lo que escribe un campo `datetime-local`: ahora más {horas}, en hora de Lima (UTC−5). */
const enLima = (horas: number) => new Date(Date.now() + horas * 3_600_000 - 5 * 3_600_000).toISOString().slice(0, 16);

// --- la pantalla ----------------------------------------------------------------------------------------------------------------------------

test("el menú lleva a Configuración y la miga dice «Plataforma / Configuración»", async ({ page }) => {
  await menuAdmin(page).getByRole("link", { name: "Configuración" }).click();

  await expect(page).toHaveURL(/\/admin\/configuracion$/);
  const miga = page.getByRole("navigation", { name: "Ubicación" });
  await expect(miga).toContainText("Plataforma");
  await expect(miga.locator("[aria-current=page]")).toHaveText("Configuración");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Configuración de la plataforma");
});

test("las tres secciones se recorren por enlaces y la actual va en la URL", async ({ page }) => {
  await abrir(page, SECCION.correo, "correo-email");
  const secciones = page.getByRole("navigation", { name: "Qué configurar" });

  await secciones.getByRole("link", { name: "Plantillas" }).click();
  await expect(page).toHaveURL(/seccion=plantillas/);
  await expect(page.getByTestId("plantilla-editor")).toBeVisible();

  await secciones.getByRole("link", { name: "Aviso de mantenimiento" }).click();
  await expect(page).toHaveURL(/seccion=aviso/);
  await expect(page.getByTestId("aviso-actual")).toBeVisible();

  await secciones.getByRole("link", { name: "Correo saliente" }).click();
  await expect(page).toHaveURL(/\/admin\/configuracion$/);
  await expect(page.getByTestId("correo-vigente")).toBeVisible();
});

// --- el remitente ---------------------------------------------------------------------------------------------------------------------------

test("el remitente: lo que no sirve se rechaza con su motivo, uno válido se guarda y vale al recargar, y se puede volver al del servidor", async ({ page }) => {
  await abrir(page, SECCION.correo, "correo-email");
  await expect(page.getByTestId("correo-origen")).toContainText("Es el de la configuración del servidor");
  await expect(page.getByTestId("correo-restablecer")).toHaveCount(0);
  await expect(page.getByTestId("correo-email")).toHaveValue("no-responder@khipu.pe");

  // Lo que el backend rechaza se dice tal cual y no cambia nada.
  await page.getByTestId("correo-email").fill("esto no es un correo");
  await page.getByTestId("correo-guardar").click();
  await expect(page.getByTestId("correo-error")).toHaveText("El correo del remitente no es una dirección de correo válida");
  await expect(page.getByTestId("correo-resultado")).toHaveCount(0);
  await expect(page.getByTestId("correo-origen")).toContainText("Es el de la configuración del servidor");

  // Uno válido se guarda.
  await page.getByTestId("correo-nombre").fill("Facturación Perú");
  await page.getByTestId("correo-email").fill("avisos@khipu.pe");
  await page.getByTestId("correo-responder-a").fill("soporte@khipu.pe");
  await page.getByTestId("correo-guardar").click();
  await expect(page.getByTestId("correo-resultado")).toHaveText("Remitente guardado: vale desde el siguiente correo.");
  await expect(page.getByTestId("correo-vigente")).toHaveText("Hoy los correos salen de: Facturación Perú <avisos@khipu.pe>");
  await expect(page.getByTestId("correo-origen")).toContainText("Lo fijó admin@khipu.pe el");

  // Vale al recargar: lo guardó el servidor, no la pantalla.
  await abrir(page, SECCION.correo, "correo-email");
  await expect(page.getByTestId("correo-email")).toHaveValue("avisos@khipu.pe");
  await expect(page.getByTestId("correo-nombre")).toHaveValue("Facturación Perú");
  await expect(page.getByTestId("correo-responder-a")).toHaveValue("soporte@khipu.pe");

  // Volver al del servidor.
  await page.getByTestId("correo-restablecer").click();
  await expect(page.getByTestId("correo-restablecer-dialogo")).toContainText("vuelven a salir de no-responder@khipu.pe");
  await page.getByTestId("correo-restablecer-confirmar").click();
  await expect(page.getByTestId("correo-origen")).toContainText("Es el de la configuración del servidor");
  await expect(page.getByTestId("correo-vigente")).toHaveText("Hoy los correos salen de: no-responder@khipu.pe");
  await expect(page.getByTestId("correo-email")).toHaveValue("no-responder@khipu.pe");
  await expect(page.getByTestId("correo-restablecer")).toHaveCount(0);
});

// --- las plantillas -------------------------------------------------------------------------------------------------------------------------

test("las plantillas listan los seis correos por su nombre y el elegido va en la URL", async ({ page }) => {
  await abrir(page, SECCION.plantillas, "plantilla-asunto");

  const lista = page.getByRole("navigation", { name: "Correos" });
  for (const nombre of ["Verificación de correo", "Restablecer la contraseña", "Bienvenida de un cliente dado de alta", "Aviso: certificado por vencer", "Aviso: certificado vencido", "Aviso: credenciales SOL"])
    await expect(lista.getByRole("link", { name: new RegExp(nombre) })).toBeVisible();
  await expect(page.getByTestId("plantilla-editor")).toHaveAttribute("data-tipo", "VERIFICACION_CORREO");

  await lista.getByRole("link", { name: /Bienvenida de un cliente/ }).click();

  await expect(page).toHaveURL(/plantilla=BIENVENIDA/);
  await expect(page.getByTestId("plantilla-editor")).toHaveAttribute("data-tipo", "BIENVENIDA");
  await expect(page.getByTestId("plantilla-asunto")).toHaveValue("Te damos la bienvenida a khipu");
});

test("un clic en una variable la pone donde está el cursor, y la vista previa la muestra con su ejemplo sin guardar nada", async ({ page }) => {
  await abrir(page, "/admin/configuracion?seccion=plantillas&plantilla=BIENVENIDA", "plantilla-asunto");
  const asunto = page.getByTestId("plantilla-asunto");
  const cuerpo = page.getByTestId("plantilla-cuerpo");
  await expect(page.getByTestId("plantilla-estado")).toHaveText("Sin cambios sin guardar.");

  await asunto.fill("Bienvenida ");
  await asunto.press("End");
  await page.getByTestId("plantilla-variable-razon_social").click();
  await expect(asunto).toHaveValue("Bienvenida {razon_social}");

  await cuerpo.click();
  await cuerpo.press("Control+End");
  await page.getByTestId("plantilla-variable-ruc").click();
  await expect(cuerpo).toHaveValue(/\{ruc\}$/);
  await expect(page.getByTestId("plantilla-estado")).toHaveText("Hay cambios sin guardar.");

  await page.getByTestId("plantilla-ver-vista-previa").click();

  await expect(page.getByTestId("plantilla-vista-previa-asunto")).toHaveText("Bienvenida PANADERIA SOL SAC");
  await expect(page.getByTestId("plantilla-vista-previa-cuerpo")).toContainText("Dimos de alta a PANADERIA SOL SAC (RUC 20100047226) en khipu.");
  await expect(page.getByTestId("plantilla-vista-previa-cuerpo")).toContainText("https://app.khipu.pe/restablecer/0a1b2c3d?invitacion=1");

  // La vista previa no guardó nada: al recargar vuelve a lo de fábrica.
  await abrir(page, "/admin/configuracion?seccion=plantillas&plantilla=BIENVENIDA", "plantilla-asunto");
  await expect(page.getByTestId("plantilla-asunto")).toHaveValue("Te damos la bienvenida a khipu");
  await expect(page.getByTestId("plantilla-origen")).toHaveText("Sale con el texto de fábrica.");
});

test("guardar un texto lo marca como editado y vale al recargar, y restaurar vuelve al de fábrica", async ({ page }) => {
  await abrir(page, "/admin/configuracion?seccion=plantillas&plantilla=RECUPERACION_CLAVE", "plantilla-asunto");
  await expect(page.getByTestId("plantilla-restaurar")).toHaveCount(0);

  await page.getByTestId("plantilla-asunto").fill("Recupera tu cuenta de khipu");
  await page.getByTestId("plantilla-cuerpo").fill("Hola. Elige una clave nueva aquí ({validez}):\n{enlace}\n\nEl equipo de khipu");
  await page.getByTestId("plantilla-guardar").click();

  await expect(page.getByTestId("plantilla-resultado")).toHaveText("Texto guardado: vale desde el siguiente correo.");
  await expect(page.getByTestId("plantilla-origen")).toContainText("Lo editó admin@khipu.pe el");
  await expect(page.locator('[data-testid="plantilla-enlace"][data-tipo="RECUPERACION_CLAVE"]')).toContainText("Editado");

  await abrir(page, "/admin/configuracion?seccion=plantillas&plantilla=RECUPERACION_CLAVE", "plantilla-asunto");
  await expect(page.getByTestId("plantilla-asunto")).toHaveValue("Recupera tu cuenta de khipu");
  await expect(page.getByTestId("plantilla-cuerpo")).toHaveValue("Hola. Elige una clave nueva aquí ({validez}):\n{enlace}\n\nEl equipo de khipu");

  await page.getByTestId("plantilla-restaurar").click();
  await expect(page.getByTestId("plantilla-restaurar-dialogo")).toContainText("«Restablecer la contraseña» vuelve a salir con su texto de fábrica");
  await page.getByTestId("plantilla-restaurar-confirmar").click();

  await expect(page.getByTestId("plantilla-origen")).toHaveText("Sale con el texto de fábrica.");
  await expect(page.getByTestId("plantilla-asunto")).toHaveValue("Restablecer contraseña");
  await expect(page.getByTestId("plantilla-restaurar")).toHaveCount(0);
});

test("un cuerpo sin el enlace indispensable se rechaza diciendo cuál falta, y no se guarda", async ({ page }) => {
  await abrir(page, "/admin/configuracion?seccion=plantillas&plantilla=VERIFICACION_CORREO", "plantilla-asunto");

  await page.getByTestId("plantilla-cuerpo").fill("Entra al portal y pide otro enlace.");
  await page.getByTestId("plantilla-guardar").click();

  await expect(page.getByTestId("plantilla-error")).toContainText("El cuerpo tiene que incluir {enlace}");
  await expect(page.getByTestId("plantilla-resultado")).toHaveCount(0);
  await expect(page.getByTestId("plantilla-restaurar")).toHaveCount(0);
});

test("una variable que ese correo no tiene se rechaza listando las que sí, tanto al guardar como en la vista previa", async ({ page }) => {
  await abrir(page, "/admin/configuracion?seccion=plantillas&plantilla=AVISO_CREDENCIALES_SOL", "plantilla-asunto");

  await page.getByTestId("plantilla-cuerpo").fill("Revisa {fecha} en {enlace}");
  await page.getByTestId("plantilla-ver-vista-previa").click();
  await expect(page.getByTestId("plantilla-error")).toContainText("Este correo no tiene la variable {fecha}");
  await expect(page.getByTestId("plantilla-error")).toContainText("{razon_social}, {ruc}, {enlace}");
  await expect(page.getByTestId("plantilla-vista-previa")).toHaveCount(0);

  await page.getByTestId("plantilla-guardar").click();
  await expect(page.getByTestId("plantilla-error")).toContainText("Este correo no tiene la variable {fecha}");
  await expect(page.getByTestId("plantilla-resultado")).toHaveCount(0);
});

test("un asunto vacío se rechaza", async ({ page }) => {
  await abrir(page, "/admin/configuracion?seccion=plantillas&plantilla=AVISO_CERTIFICADO_VENCIDO", "plantilla-asunto");

  await page.getByTestId("plantilla-asunto").fill("");
  await page.getByTestId("plantilla-guardar").click();

  await expect(page.getByTestId("plantilla-error")).toHaveText("El asunto no puede estar vacío");
});

// --- el aviso de mantenimiento --------------------------------------------------------------------------------------------------------------
// El aviso es UNO solo para todo el mock: los dos tests que lo publican van en serie (Playwright corre en paralelo hasta los tests de un mismo archivo).
test.describe("aviso de mantenimiento", () => {
  test.describe.configure({ mode: "serial" });

  test("el aviso: lo que no sirve se rechaza, uno válido se publica y lo ven los clientes —también antes de iniciar sesión—, y al retirarlo dejan de verlo", async ({ page, browser }) => {
    await abrir(page, SECCION.aviso, "aviso-texto");
    await expect(page.getByTestId("aviso-ninguno")).toBeVisible();

    // Sin texto ni fin lo dice la pantalla; el orden de las fechas lo dice el backend.
    await page.getByTestId("aviso-publicar").click();
    await expect(page.getByText("Escribe el texto del aviso.")).toBeVisible();
    await expect(page.getByText("Indica hasta cuándo se muestra.")).toBeVisible();
    await page.getByTestId("aviso-texto").fill("Mantenimiento programado esta noche");
    await page.getByTestId("aviso-desde").fill(enLima(3));
    await page.getByTestId("aviso-hasta").fill(enLima(1));
    await page.getByTestId("aviso-publicar").click();
    await expect(page.getByTestId("aviso-error")).toHaveText("El aviso tiene que terminar después de empezar");
    await expect(page.getByTestId("aviso-ninguno")).toBeVisible();

    // Uno válido y vigente ya se publica.
    await page.getByTestId("aviso-desde").fill(enLima(-1));
    await page.getByTestId("aviso-hasta").fill(enLima(3));
    await expect(page.getByTestId("aviso-vista-previa")).toContainText("Mantenimiento programado esta noche");
    await page.getByTestId("aviso-publicar").click();
    await expect(page.getByTestId("aviso-estado")).toHaveText("Se está mostrando ahora.");
    await expect(page.getByTestId("aviso-publicado-texto")).toHaveText("Mantenimiento programado esta noche");
    await expect(page.getByTestId("aviso-publicar")).toContainText("Reemplazar aviso");

    try {
      // Un visitante sin sesión lo ve en el inicio de sesión...
      const visitante = await browser.newContext();
      const login = await visitante.newPage();
      await login.goto("/login");
      await expect(login.getByTestId("banner-de-mantenimiento-texto")).toHaveText("Mantenimiento programado esta noche");
      await expect(login.getByTestId("banner-de-mantenimiento")).toContainText("Hasta");

      // ...y un cliente, en su portal.
      await login.getByLabel("Correo electrónico").fill("demo@example.com");
      await login.getByLabel("Contraseña").fill("Passw0rd1");
      await login.getByRole("button", { name: "Iniciar sesión" }).click();
      await expect(login).toHaveURL(/\/comprobantes/);
      await expect(login.getByTestId("banner-de-mantenimiento-texto")).toHaveText("Mantenimiento programado esta noche");
      await visitante.close();
    } finally {
      // Retirarlo: los clientes dejan de verlo, y no queda un aviso suelto para las demás pruebas.
      await page.getByTestId("aviso-retirar").click();
      await expect(page.getByTestId("aviso-retirar-dialogo")).toContainText("Los clientes dejan de ver el aviso");
      await page.getByTestId("aviso-retirar-confirmar").click();
      await expect(page.getByTestId("aviso-ninguno")).toBeVisible();
    }

    const despues = await browser.newContext();
    const loginDespues = await despues.newPage();
    await loginDespues.goto("/login");
    await expect(loginDespues.getByRole("heading", { name: "Inicia sesión" })).toBeVisible();
    await expect(loginDespues.getByTestId("banner-de-mantenimiento")).toHaveCount(0);
    await despues.close();
  });

  test("un aviso programado para después se guarda pero los clientes todavía no lo ven", async ({ page, browser }) => {
    await abrir(page, SECCION.aviso, "aviso-texto");
    await expect(page.getByTestId("aviso-ninguno")).toBeVisible();

    await page.getByTestId("aviso-texto").fill("Mañana hay mantenimiento");
    await page.getByTestId("aviso-desde").fill(enLima(24));
    await page.getByTestId("aviso-hasta").fill(enLima(27));
    await page.getByTestId("aviso-publicar").click();
    await expect(page.getByTestId("aviso-estado")).toHaveText("Programado: todavía no se muestra.");

    try {
      const visitante = await browser.newContext();
      const login = await visitante.newPage();
      await login.goto("/login");
      await expect(login.getByRole("heading", { name: "Inicia sesión" })).toBeVisible();
      await expect(login.getByTestId("banner-de-mantenimiento")).toHaveCount(0);
      await visitante.close();
    } finally {
      await page.getByTestId("aviso-retirar").click();
      await page.getByTestId("aviso-retirar-confirmar").click();
      await expect(page.getByTestId("aviso-ninguno")).toBeVisible();
    }
  });
});
