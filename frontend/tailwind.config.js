/** @type {import('tailwindcss').Config} */
export default {
  darkMode: "media",
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        // Greenbar paper / stamped-ink green (light) — shared with the marketing site.
        paper: "var(--paper)",
        bar: "var(--bar)",
        ink: "var(--ink)",
        "ink-soft": "var(--ink-soft)",
        accent: "var(--accent)",
        rule: "var(--rule)",
        card: "var(--card)",
      },
      fontFamily: {
        mono: ['"Courier New"', "Courier", "ui-monospace", "monospace"],
      },
    },
  },
  plugins: [],
};
