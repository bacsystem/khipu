"use client";

import { ApiReferenceReact } from "@scalar/api-reference-react";
import "@scalar/api-reference-react/style.css";

const CUSTOM_CSS = `
:root {
  --scalar-font: var(--font-sans);
  --scalar-font-code: var(--font-mono);
  --scalar-radius: 8px;
}
.light-mode {
  --scalar-background-1: #ffffff;
  --scalar-background-2: #f5f6f8;
  --scalar-background-3: #ececef;
  --scalar-color-1: #151a21;
  --scalar-color-2: #5b6472;
  --scalar-color-3: #5b6472;
  --scalar-color-accent: #1e3a5f;
  --scalar-border-color: #dde1e6;
  --scalar-button-1: #1e3a5f;
  --scalar-button-1-hover: #16293f;
  --scalar-button-1-color: #f5f6f8;
  --scalar-link-color: #1e3a5f;
}
.dark-mode {
  --scalar-background-1: #171e27;
  --scalar-background-2: #10151c;
  --scalar-background-3: #1b2029;
  --scalar-color-1: #e7eaee;
  --scalar-color-2: #98a2af;
  --scalar-color-3: #98a2af;
  --scalar-color-accent: #7fa6d1;
  --scalar-border-color: rgba(255, 255, 255, 0.12);
  --scalar-button-1: #7fa6d1;
  --scalar-button-1-hover: #6a92bd;
  --scalar-button-1-color: #0f1620;
  --scalar-link-color: #7fa6d1;
}
`;

export function ApiReference({
  spec,
  baseServerURL,
  apiKey,
}: {
  spec: Record<string, unknown>;
  baseServerURL: string;
  apiKey?: string;
}) {
  return (
    <ApiReferenceReact
      configuration={{
        content: spec,
        theme: "default",
        customCss: CUSTOM_CSS,
        baseServerURL,
        authentication: apiKey ? { securitySchemes: { ApiKey: { value: apiKey } } } : undefined,
      }}
    />
  );
}
