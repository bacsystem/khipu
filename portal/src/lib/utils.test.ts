import { describe, expect, it } from "vitest";
import { cn } from "./utils";

describe("cn", () => {
  it("combina clases y descarta valores falsy", () => {
    expect(cn("a", false && "b", "c")).toBe("a c");
  });
});
