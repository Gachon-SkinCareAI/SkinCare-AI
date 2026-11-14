// React Native has no DOM: lowercase JSX such as <br>, <div> or <span> renders on
// the web preview but crashes on Android ("View config getter callback for component
// `br` must be a function"). Keep the app code free of web-only elements.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";

const ROOTS = ["src", "App.js"];
const WEB_TAGS = /<(br|div|span|p|img|a|button|input|textarea|select|label|form|h[1-6]|ul|ol|li|section|header|footer|nav|main|strong|em|b|i|table)(\s|\/?>)/;

function files(path) {
  if (statSync(path).isFile()) return path.endsWith(".js") ? [path] : [];
  return readdirSync(path).flatMap((name) => files(join(path, name)));
}

test("app code uses only React Native components (no web-only JSX tags)", () => {
  const found = [];
  for (const file of ROOTS.flatMap(files)) {
    // Blank out /* ... */ comments (keeping line breaks so line numbers stay right).
    const source = readFileSync(file, "utf8").replace(/\/\*[\s\S]*?\*\//g, (c) => c.replace(/[^\n]/g, " "));
    source.split("\n").forEach((line, i) => {
      const code = line.replace(/\/\/.*$/, "");
      const match = code.match(WEB_TAGS);
      if (match) found.push(`${file}:${i + 1} <${match[1]}>`);
    });
  }
  assert.deepEqual(found, [], `web-only tags found:\n${found.join("\n")}`);
});
