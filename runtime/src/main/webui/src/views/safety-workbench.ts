import {
  columns, dataTable, tabs, panel, html,
  lookup,
} from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";
import type { DataSourceBinding } from "@casehubio/pages-data";

export function safetyWorkbench(trialId: string): Component {
  const aeTable = dataTable({
    title: "Adverse Events",
    lookup: lookup("adverse-events"),
    sortable: true,
    pageSize: 25,
    selection: "single",
    selectionKey: "id",
    columns: [
      { id: "grade" as never, name: "Grade", expression: '(value = "GRADE_4" or value = "GRADE_5") ? "🔴 " & value : value = "GRADE_3" ? "🟠 " & value : value' },
      { id: "eventType" as never, name: "Event Type" },
      { id: "patientId" as never, name: "Patient", expression: 'value ? $substring(value, 0, 8) & "..." : ""' },
      { id: "siteName" as never, name: "Site" },
      { id: "slaTimeRemainingHours" as never, name: "SLA Remaining", expression: '$number(value) < 0 ? "🔴 OVERDUE" : $number(value) < 4 ? "🟠 " & $string($round($number(value))) & "h" : $number(value) < 12 ? "🟡 " & $string($round($number(value))) & "h" : "🟢 " & $string($round($number(value))) & "h"' },
      { id: "escalationStatus" as never, name: "Escalation" },
      { id: "regulatorySubmissionStatus" as never, name: "IND Status" },
    ],
    rowStyle: [
      { condition: '#{row.grade} == "GRADE_4" || #{row.grade} == "GRADE_5"', style: { "background-color": "var(--pages-red-2, #fdf0f0)" } },
      { condition: '#{row.slaTimeRemainingHours} < 0', style: { "background-color": "var(--pages-red-3, #fde0e0)" } },
    ],
    filter: { enabled: true },

  });

  const detailTabs = tabs(
    ["Overview", panel("AE Overview",
      html('<div id="ae-overview"><p style="color: var(--pages-neutral-9); font-style: italic; padding: 1rem;">Select an adverse event from the list to view details.</p></div>'),
    )],
    ["SUSAR Evaluation", panel("SUSAR Evaluation",
      html(`<blocks-approval-gate id="susar-gate"
        data-trial-id="${trialId}" data-source-dataset="adverse-events"
        prompt="Review SUSAR determination for this adverse event"
        context-text="Grade 4+ unexpected suspected adverse reaction — SUSAR criteria evaluation"
      ></blocks-approval-gate>`),
    )],
    ["Trust & Attestation", panel("Trust Feedback",
      html('<trust-feedback-display id="ae-trust-feedback"></trust-feedback-display>'),
    )],
    ["Regulatory", panel("Regulatory Status",
      html('<sla-breach-policy-indicator id="ae-sla-breach"></sla-breach-policy-indicator>'),
    )],
    ["Precedents", panel("Similar Past Cases",
      dataTable({
        title: "AE Precedents",
        lookup: lookup("ae-precedents"),
        sortable: true,
        pageSize: 10,
        columns: [
          { id: "similarity" as never, name: "Similarity", expression: '$string($round($number(value))) & "%"' },
          { id: "grade" as never, name: "Grade" },
          { id: "outcome" as never, name: "Outcome" },
          { id: "resolutionTime" as never, name: "Resolution Time" },
          { id: "reportedDate" as never, name: "Reported" },
        ],
      }),
    )],
    ["Audit Trail", panel("Ledger Entries",
      dataTable({
        title: "Audit Trail",
        lookup: lookup("ledger-entries"),
        sortable: true,
        pageSize: 25,
        columns: [
          { id: "occurredAt" as never, name: "Timestamp" },
          { id: "entryType" as never, name: "Type" },
          { id: "actorId" as never, name: "Actor" },
          { id: "subjectId" as never, name: "Subject" },
          { id: "digest" as never, name: "Digest", expression: 'value ? $substring(value, 0, 16) & "..." : ""' },
        ],
        filter: { enabled: true },
      }),
    )],
    ["Grade History", panel("Grade History",
      html(`<clinical-ae-grade-history id="ae-grade-history" data-trial-id="${trialId}" data-source-dataset="adverse-events"></clinical-ae-grade-history>`),
    )],
    ["Regrade", panel("Regrade Assessment",
      html(`<clinical-ae-regrade id="ae-regrade" data-trial-id="${trialId}" data-source-dataset="adverse-events"></clinical-ae-regrade>`),
    )],
  );

  return columns([5, 7],
    [aeTable],
    [detailTabs],
  );
}

export const safetyWorkbenchDatasets: DataSourceBinding[] = [];
