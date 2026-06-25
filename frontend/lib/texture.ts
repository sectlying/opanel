import { type Item, versions } from "minecraft-textures";
import { coerce, compare } from "semver";

export async function getTextures(version: string): Promise<Item[] | null> {
  let suitableVersion: string | null = null;
  for(const textureVersion of versions) {
    if(compare(coerce(textureVersion) ?? "", coerce(version) ?? "") > 0) break;
    suitableVersion = textureVersion;
  }

  if(suitableVersion == null) return null;
  
  // Exclude unsupported versions: 1.12, 1.13, 1.14, 1.15, 1.17, 1.18
  // along with the unused *.id.json siblings
  return (await import(
    /* webpackExclude: /\.id\.json$|^\.\/1\.(1[2-5]|17|18)\.json$/ */
    `minecraft-textures-json/${suitableVersion}.json`
  )).items;
}
