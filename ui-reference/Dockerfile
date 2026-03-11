FROM node:22-alpine AS build
WORKDIR /app
ARG VITE_API_BASE=/api
ARG VITE_UI_COMMIT=unknown
ARG VITE_UI_BRANCH=unknown
ENV VITE_API_BASE=$VITE_API_BASE
ENV VITE_UI_COMMIT=$VITE_UI_COMMIT
ENV VITE_UI_BRANCH=$VITE_UI_BRANCH
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/templates/default.conf.template
ENV BACKEND_SCHEME=http
ENV BACKEND_HOST=backend:8080
EXPOSE 3001
