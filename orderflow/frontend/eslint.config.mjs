import js from "@eslint/js";
import tseslint from "typescript-eslint";
import hooks from "eslint-plugin-react-hooks";
import next from "@next/eslint-plugin-next";
import { defineConfig, globalIgnores } from "eslint/config";
export default defineConfig([
  globalIgnores([
    ".next/**",
    "out/**",
    "coverage/**",
    "playwright-report/**",
    "test-results/**",
    "output/**",
    "next-env.d.ts",
  ]),
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ["**/*.{ts,tsx}"],
    plugins: { "react-hooks": hooks, "@next/next": next },
    rules: {
      ...hooks.configs.flat.recommended.rules,
      ...next.configs.recommended.rules,
      ...next.configs["core-web-vitals"].rules,
    },
  },
  {
    files: ["**/*.{ts,tsx,mjs}"],
    languageOptions: {
      globals: {
        process: "readonly",
        console: "readonly",
        setTimeout: "readonly",
        clearTimeout: "readonly",
        setInterval: "readonly",
        clearInterval: "readonly",
        fetch: "readonly",
        document: "readonly",
        window: "readonly",
        navigator: "readonly",
        crypto: "readonly",
        AbortController: "readonly",
        AbortSignal: "readonly",
        Headers: "readonly",
        URL: "readonly",
        URLSearchParams: "readonly",
        TextDecoder: "readonly",
        structuredClone: "readonly",
        React: "readonly",
      },
    },
  },
]);
