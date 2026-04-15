import type { ArrayItem, GithubReleaseResponse } from "./types";
import axios from "axios";
import { compare } from "semver";
import { getSettings } from "./settings";
import { isPreviewVersion } from "./utils";
import { version } from "./global";

export async function checkUpdate(): Promise<{
  hasNewUpdate: boolean
  releaseInfo: ArrayItem<GithubReleaseResponse> | null
}> {
  const previewEnabled = getSettings("system.preview-channel");
  const { data } = await axios.get<GithubReleaseResponse>("https://api.github.com/repos/opanel-mc/opanel/releases");

  for(const release of data) {
    const tagName = release.tag_name.replace(/(?<!-)rc/g, "-rc");
    if(!previewEnabled && (release.prerelease || isPreviewVersion(tagName))) continue;
    if(compare(tagName, version) > 0) {
      return {
        hasNewUpdate: true,
        releaseInfo: release
      };
    }
  }

  return {
    hasNewUpdate: false,
    releaseInfo: null
  };
}
