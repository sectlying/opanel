import "@testing-library/jest-dom/vitest";
import { vi } from "vitest";
import { isMobileMockState } from "./mock-state";

vi.mock("@/hooks/use-mobile", () => ({
  useIsMobile: () => isMobileMockState.current
}));

vi.mock("next/font/local", () => ({
  default: () => ({
    className: "mocked-font-class",
    variable: "--mocked-font-variable",
    style: { fontFamily: "mocked-font" }
  })
}));

vi.mock("@/lib/i18n", () => ({
  $: (id: string, ...args: unknown[]) => `[${id}]${args.length > 0 ? `(${args.join(",")})` : ""}`,
  $mc: (id: string) => `[${id}]`,
  localize: (id: string) => `[${id}]`,
  localizeRich: (id: string, ...args: unknown[]) => `[${id}]${args.length > 0 ? `(${args.join(",")})` : ""}`,
}));

vi.mock("sonner", () => ({
  toast: {
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn()
  }
}));

vi.mock("@/components/monaco-editor", () => ({
  default: function MockMonacoEditor({
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
