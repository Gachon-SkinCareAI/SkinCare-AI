// Icons must be inlined (SvgXml), not loaded from asset files (SvgUri): release APKs
// rename bundled resources and SvgUri then draws nothing on Android.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { OUTPUT, render } from "../scripts/generate-svg-icons.mjs";

test("svgIcons.js is up to date with assets/**/*.svg", () => {
  assert.equal(
    readFileSync(OUTPUT, "utf8"),
    render(),
    "run: node scripts/generate-svg-icons.mjs",
  );
});

test("app code does not draw SVG files through SvgUri", () => {
  const files = (dir) =>
    readdirSync(dir).flatMap((n) => {
      const p = join(dir, n);
      return statSync(p).isDirectory() ? files(p) : p.endsWith(".js") ? [p] : [];
    });
  const offenders = files("src").filter((f) => /\bSvgUri\b/.test(readFileSync(f, "utf8")));
  assert.deepEqual(offenders, []);
});
