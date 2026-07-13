import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// The built bundle is copied into the Spring Boot jar's static resources and served
// same-origin at "/", so no base path or CORS is needed in production. In dev, Vite
// proxies the API to the running backend so the browser still sees one origin.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: "dist",
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: process.env.RETROSPOOL_API ?? "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
