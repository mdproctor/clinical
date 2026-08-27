import type { Component } from "@casehubio/pages-ui";

export interface SchemaFormConfig {
  schema: {
    properties: Record<string, Record<string, unknown>>;
    required?: string[];
  };
  excludeFields?: string[];
  labels?: Record<string, string>;
  fieldOrder?: string[];
  validateOnBlur?: boolean;
  forceCreate?: boolean;
}

export function schemaForm(config: SchemaFormConfig): Component {
  return Object.freeze({
    type: "schema-form" as never,
    props: Object.freeze({ ...config }),
  }) as Component;
}
