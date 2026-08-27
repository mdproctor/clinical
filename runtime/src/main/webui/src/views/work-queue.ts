import { rows, html } from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";
import type { DataSourceBinding } from "@casehubio/pages-data";

export function workQueue(): Component {
  return rows(
    html('<work-item-inbox endpoint="/api/workitems?candidateGroups=clinical"></work-item-inbox>'),
  );
}

export const workQueueDatasets: DataSourceBinding[] = [];
