import {
  rows, columns, tabs, dataTable, metric, barChart, pieChart, html,
  lookup, groupBy, filterBy, col, count, sum,
} from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";
import type { DataSourceBinding } from "@casehubio/pages-data";

export function operations(): Component {
  const trialDashboard = rows(
    columns([3, 3, 3, 3],
      [metric({ title: "Trial Phase", lookup: lookup("trial-summary", groupBy(null, col("phase"))) })],
      [metric({ title: "Total Enrolled", lookup: lookup("trial-summary", groupBy(null, col("totalEnrolled"))) })],
      [metric({ title: "Adverse Events", lookup: lookup("trial-summary", groupBy(null, col("totalAdverseEvents"))) })],
      [metric({ title: "Protocol Deviations", lookup: lookup("trial-summary", groupBy(null, col("totalDeviations"))) })],
    ),
    barChart({
      title: "Enrollment by Site: Target vs Actual",
      lookup: lookup("sites", groupBy("siteName", col("siteName"), col("targetEnrollment"), col("enrolledCount"))),
    }),
    dataTable({
      title: "Recent Activity",
      lookup: lookup("ledger-entries"),
      sortable: true,
      pageSize: 10,
      columns: [
        { id: "occurredAt" as never, name: "Time", expression: 'value ? $substring(value, 0, 16) : ""' },
        { id: "entryType" as never, name: "Event Type", expression: 'value' },
        { id: "actorId" as never, name: "Actor", expression: 'value ? $substring(value, 0, 12) & "..." : ""' },
        { id: "subjectId" as never, name: "Subject", expression: 'value ? $substring(value, 0, 8) & "..." : ""' },
      ],
    }),
  );

  const trustGovernance = rows(
    dataTable({
      title: "Agent Trust Scores",
      lookup: lookup("agents"),
      sortable: true,
      pageSize: 25,
      columns: [
        { id: "capability" as never, name: "Capability" },
        { id: "trustScore" as never, name: "Trust Score", expression: '$number(value) >= 0.8 ? "🟢 " & value : $number(value) >= 0.6 ? "🟡 " & value : $number(value) >= 0.4 ? "🟠 " & value : "🔴 " & value' },
        { id: "trustDimension" as never, name: "Dimension" },
        { id: "maturityPhase" as never, name: "Maturity", expression: '$number(value) = 0 ? "🔵 Bootstrap" : $number(value) = 1 ? "🟡 Emerging" : "🟢 Established"' },
        { id: "decisionCount" as never, name: "Decisions" },
        { id: "endorsementRatio" as never, name: "Endorsement", expression: 'value ? $string($round($number(value) * 100, 1)) & "%" : "—"' },
      ],
    }),
  );

  const slaHealth = rows(
    pieChart({
      title: "Work Items by SLA Status",
      lookup: lookup("work-items", groupBy("slaStatus", col("slaStatus"), count("title"))),
    }),
  );

  const compliance = rows(
    html(`<regulatory-compliance-summary></regulatory-compliance-summary>`),
  );

  const gdpr = rows(
    html(`<gdpr-erasure-action
      endpoint="/api/gdpr/erasure/patients"
      subject-label="Patient"
    ></gdpr-erasure-action>`),
  );

  return tabs(
    ["Trial Dashboard", trialDashboard],
    ["Trust & Governance", trustGovernance],
    ["SLA Health", slaHealth],
    ["Compliance", compliance],
    ["GDPR", gdpr],
  );
}

export const operationsDatasets: DataSourceBinding[] = [];
