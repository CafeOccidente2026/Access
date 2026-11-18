# Etapa de build: misma version de Node que el CI
FROM node:22.22.3-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Etapa de ejecucion: Caddy sirve la web, manda /api al backend y hace el HTTPS (deploy/Caddyfile)
FROM caddy:2-alpine
COPY --from=build /app/dist/cafe-occidente-frontend/browser /srv
