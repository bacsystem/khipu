FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
# khipu-design-system es una dependencia de GitHub por tag: npm baja su tarball por HTTPS (sin `git`, que esta imagen no
# trae), instala sus devDependencies y corre su `prepare`, que compila `dist/`. Probado con `docker build` (#16).
RUN npm ci

FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN mkdir -p public && npm run build

FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
# Railway (y cualquier PaaS) inyecta PORT; el server de Next standalone lo respeta. HOSTNAME explícito para escuchar en todas
# las interfaces del contenedor, no solo en localhost.
ENV HOSTNAME=0.0.0.0
# Cuánto mantiene el portal una conexión inactiva, en ms (#610). El default de Node, 5 s, es menor que el del proxy de delante (Railway
# reutiliza sus conexiones unos 60 s): el proxy manda una petición por una conexión que el portal acaba de cerrar y el cliente recibe un
# corte (medido con k6: 68 logins cortados con 5 s, 0 con 65 s). Tiene que ser mayor que el del proxy; el server.js de Next la lee.
ENV KEEP_ALIVE_TIMEOUT=65000
RUN addgroup -S nextjs && adduser -S nextjs -G nextjs
COPY --from=build /app/public ./public
COPY --from=build --chown=nextjs:nextjs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nextjs /app/.next/static ./.next/static
# Punto de entrada (#618): con PORTAL_PROCESOS=N levanta N procesos de server.js que comparten el puerto; sin la variable, uno, como antes.
COPY --from=build --chown=nextjs:nextjs /app/procesos.cjs ./procesos.cjs
USER nextjs
EXPOSE 3000
CMD ["node", "procesos.cjs"]
