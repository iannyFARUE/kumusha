# syntax=docker/dockerfile:1

# ---- dependencies ----
FROM node:22-alpine AS deps

WORKDIR /app
COPY package.json package-lock.json ./
# ci rather than install, so the image is built from the committed lockfile exactly
RUN npm ci

# ---- build ----
FROM node:22-alpine AS build

WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Next inlines NEXT_PUBLIC_ variables at build time, so this has to arrive as a build argument.
# Supplying it at run time would be too late and the bundle would keep the localhost fallback.
# The value must be reachable from the browser, not from inside the container network, because
# that is where these requests are made.
ARG NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL

RUN npm run build

# ---- runtime ----
FROM node:22-alpine AS runtime

WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# node:22-alpine ships a "node" user already, so there is no user to create
USER node

# The standalone output carries its own minimal server and node_modules; static assets and
# anything under public are served alongside it and are not part of that bundle.
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static

EXPOSE 3000

HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=3 \
    CMD node -e "fetch('http://localhost:3000/').then(r => process.exit(r.ok ? 0 : 1)).catch(() => process.exit(1))"

CMD ["node", "server.js"]
