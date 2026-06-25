import type { NextConfig } from "next";
import path from "path";

// `minecraft-textures` only exports individual `*.json` files via its `exports` field,
// not the containing directory. Aliasing it lets the bundler enumerate the folder for
// the dynamic `import()` in `lib/texture.ts` without hitting the exports restriction.
const texturesJsonDir = path.join(
  process.cwd(),
  "node_modules/minecraft-textures/dist/textures/json"
);

const nextConfig: NextConfig = {
  distDir: "build",
  output: "export",
  trailingSlash: true,
  skipTrailingSlashRedirect: true,
  reactStrictMode: false,
  webpack: (config) => {
    config.resolve.alias["minecraft-textures-json"] = texturesJsonDir;
    return config;
  },
  turbopack: {
    resolveAlias: {
      "minecraft-textures-json": texturesJsonDir
    }
  }
};

export default nextConfig;
