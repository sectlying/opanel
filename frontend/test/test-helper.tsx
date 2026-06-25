import { isMobileMockState } from "./mock-state";

export function mockUseIsMobile(initialValue = false) {
  isMobileMockState.current = initialValue;
}

export function setMockUseIsMobile(value: boolean) {
  isMobileMockState.current = value;
}
