import { beforeEach, vi } from "vitest";
import { changeSettings } from "@/lib/settings";
import { isMobileMockState } from "./use-mobile-mock";

export function mockRealI18n() {
  vi.unmock("@/lib/i18n");

  beforeEach(() => {
    changeSettings("system.language", "zh-cn");
  });
}

export function mockUseIsMobile(initialValue = false) {
  isMobileMockState.current = initialValue;
}

export function setMockUseIsMobile(value: boolean) {
  isMobileMockState.current = value;
}

export function mockMonacoEditor() {
  vi.mock("next/dynamic", () => ({
    default: () => function MockMonacoEditor({
      value,
      theme,
      onChange
    }: {
      value?: string
      theme?: string
      onChange?: (value?: string) => void
    }) {
      return (
        <textarea
          data-testid="monaco-editor"
          data-theme={theme ?? ""}
          value={value ?? ""}
          onChange={(e) => onChange?.(e.target.value)}/>
      );
    }
  }));
}
