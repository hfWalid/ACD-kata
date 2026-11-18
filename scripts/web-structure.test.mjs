import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { describe, it } from "node:test";
import { fileURLToPath } from "node:url";

/**
 * Guard for the atomic layout of the interface, the web counterpart of the backend's ArchUnit
 * rules. It reads the sources instead of instantiating them: a misplaced import fails the build
 * here rather than surprising a reviewer.
 */
const APP = resolve(
  dirname(fileURLToPath(import.meta.url)),
  "..",
  "web",
  "src",
  "app",
);
const SUFFIXES = ["", ".ts", ".html", "/index.ts"];

function sources(directory = APP) {
  return readdirSync(directory).flatMap((entry) => {
    const path = join(directory, entry);
    if (statSync(path).isDirectory()) {
      return sources(path);
    }
    if (!path.endsWith(".ts")) {
      return [];
    }
    const imports = [
      ...readFileSync(path, "utf8").matchAll(/from\s+'(\.[^']+)'/g),
    ].map((match) => ({
      specifier: match[1],
      target: relative(
        APP,
        resolve(dirname(path), match[1].replace(/\.(ts|html)$/, "")),
      ),
    }));
    return [{ path: relative(APP, path), imports }];
  });
}

const all = sources();
const violations = (prefix, forbidden) =>
  all
    .filter((source) => source.path.startsWith(prefix))
    .flatMap((source) =>
      source.imports
        .filter((entry) =>
          forbidden.some((target) => entry.target.startsWith(target)),
        )
        .map((entry) => `${source.path} imports ${entry.specifier}`),
    );

const rules = [
  [
    "the data layer stays free of UI and shared components",
    "features/documents/data/",
    ["features/documents/pages", "features/documents/ui", "shared/"],
  ],
  [
    "atoms stay at the bottom of the cascade",
    "features/documents/ui/atoms/",
    [
      "features/documents/ui/molecules",
      "features/documents/ui/organisms",
      "features/documents/pages",
      "shared/",
    ],
  ],
  [
    "molecules stay below organisms and pages",
    "features/documents/ui/molecules/",
    ["features/documents/ui/organisms", "features/documents/pages"],
  ],
  [
    "organisms stay free of pages",
    "features/documents/ui/organisms/",
    ["features/documents/pages"],
  ],
  [
    "core ignores the feature and the shared UI",
    "core/",
    ["features/", "shared/", "app."],
  ],
  ["shared ignores the feature", "shared/", ["features/", "app."]],
];

describe("atomic structure of web/src/app", () => {
  for (const [name, prefix, forbidden] of rules) {
    it(name, () => assert.deepEqual(violations(prefix, forbidden), []));
  }

  it("only lets the shell and the router reach a feature", () => {
    const crossFeature = all
      .filter(
        (source) =>
          !source.path.startsWith("features/") &&
          source.path !== "app.routes.ts",
      )
      .flatMap((source) =>
        source.imports
          .filter((entry) => entry.target.startsWith("features/"))
          .map((entry) => `${source.path} imports ${entry.specifier}`),
      );

    assert.deepEqual(crossFeature, []);
  });

  it("resolves every relative import to a file that exists", () => {
    const missing = all.flatMap((source) =>
      source.imports
        .filter(
          (entry) => !SUFFIXES.some((suffix) => exists(entry.target + suffix)),
        )
        .map((entry) => `${source.path} imports ${entry.specifier}`),
    );

    assert.deepEqual(missing, []);
  });
});

function exists(path) {
  try {
    return statSync(resolve(APP, path)).isFile();
  } catch {
    return false;
  }
}
