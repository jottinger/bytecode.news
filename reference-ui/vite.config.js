import { defineConfig } from "vite";

export default defineConfig({
  server: {
    port: 3003,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ""),
      },
    },
  },
});
