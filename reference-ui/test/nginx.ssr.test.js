import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

describe("docker nginx SSR routing", () => {
  it("routes crawler traffic to backend SSR endpoints", () => {
    const conf = readFileSync(resolve(process.cwd(), "nginx.conf"), "utf8");

    expect(conf).toContain("map $http_user_agent $is_crawler");
    expect(conf).toContain("location = /index.html");
    expect(conf).toContain("no-cache, no-store, must-revalidate");
    expect(conf).toContain("location /assets/");
    expect(conf).toContain("max-age=31536000, immutable");
    expect(conf).toContain("proxy_pass ${BACKEND_SCHEME}://${BACKEND_HOST}/;");
    expect(conf).toContain("proxy_ssl_server_name on;");
    expect(conf).toContain("proxy_set_header Host ${BACKEND_HOST};");
    expect(conf).toContain("proxy_set_header X-Forwarded-Host $host;");
    expect(conf).toContain("proxy_pass ${BACKEND_SCHEME}://${BACKEND_HOST}/sitemap.xml;");
    expect(conf).toContain("proxy_pass ${BACKEND_SCHEME}://${BACKEND_HOST}/feed.xml;");
    expect(conf).toContain("location /posts/");
    expect(conf).toContain("rewrite ^/posts/(.*)$ /ssr/posts/$1 last;");
    expect(conf).toContain("location /pages/");
    expect(conf).toContain("rewrite ^/pages/(.*)$ /ssr/pages/$1 last;");
    expect(conf).toContain("location = /about");
    expect(conf).toContain("rewrite ^ /ssr/pages/about last;");
    expect(conf).toContain("location /ssr/");
    expect(conf).toContain("internal;");
    expect(conf).toContain("proxy_pass ${BACKEND_SCHEME}://${BACKEND_HOST}/ssr/;");
    expect(conf).toContain("location / {");
    expect(conf).toContain("rewrite ^/$ /ssr/ last;");
  });

  it("keeps docker image configured to use nginx template with backend host", () => {
    const dockerfile = readFileSync(resolve(process.cwd(), "Dockerfile"), "utf8");

    expect(dockerfile).toContain("COPY nginx.conf /etc/nginx/templates/default.conf.template");
    expect(dockerfile).toContain("ENV BACKEND_SCHEME=http");
    expect(dockerfile).toContain("ENV BACKEND_HOST=backend:8080");
    expect(dockerfile).toContain("EXPOSE 3001");
  });
});
