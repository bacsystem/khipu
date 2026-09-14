import { render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("@scalar/api-reference-react", () => ({
  ApiReferenceReact: () => null,
}));
vi.mock("@scalar/api-reference-react/style.css", () => ({}));

import { ApiReference } from "./api-reference";

describe("ApiReference", () => {
  it("limpia light-mode/dark-mode de document.body al desmontar", () => {
    document.body.classList.add("dark-mode");

    const { unmount } = render(<ApiReference spec={{}} baseServerURL="http://localhost:8080" />);
    expect(document.body.classList.contains("dark-mode")).toBe(true);

    unmount();

    expect(document.body.classList.contains("dark-mode")).toBe(false);
    expect(document.body.classList.contains("light-mode")).toBe(false);
  });
});
