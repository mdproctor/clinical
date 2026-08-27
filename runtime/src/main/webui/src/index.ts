import { loadSite } from "@casehubio/pages-runtime";
import { onPagesEvent } from "@casehubio/pages-component";
import "@casehubio/blocks-ui-work-item-inbox";
import { app } from "./app.js";
import { ClinicalCommitmentLifecycle } from "./components/commitment-lifecycle.js";
import { ClinicalCbrPrecedentsPanel } from "./components/cbr-precedents-panel.js";
import { ClinicalTrustFeedbackDisplay } from "./components/trust-feedback-display.js";
import { ClinicalRegulatoryComplianceSummary } from "./components/regulatory-compliance-summary.js";
import { ClinicalGdprErasureAction } from "./components/gdpr-erasure-action.js";
import { ClinicalSlaBreachPolicyIndicator } from "./components/sla-breach-policy-indicator.js";
import { ClinicalAeGradeHistory } from "./components/ae-grade-history.js";
import { ClinicalAeRegrade } from "./components/ae-regrade.js";

const components: [string, CustomElementConstructor][] = [
  ["commitment-lifecycle", ClinicalCommitmentLifecycle],
  ["cbr-precedents-panel", ClinicalCbrPrecedentsPanel],
  ["trust-feedback-display", ClinicalTrustFeedbackDisplay],
  ["regulatory-compliance-summary", ClinicalRegulatoryComplianceSummary],
  ["gdpr-erasure-action", ClinicalGdprErasureAction],
  ["sla-breach-policy-indicator", ClinicalSlaBreachPolicyIndicator],
  ["clinical-ae-grade-history", ClinicalAeGradeHistory],
  ["clinical-ae-regrade", ClinicalAeRegrade],
];

for (const [name, ctor] of components) {
  if (!customElements.get(name)) customElements.define(name, ctor);
}

const CLINICAL_IDENTITY = {
  userId: "demo-coordinator",
  displayName: "Demo Coordinator",
  groups: ["SPONSOR", "INVESTIGATOR", "COORDINATOR", "MONITOR"],
};

function configureWorkItemInbox() {
  const inbox = document.querySelector("work-item-inbox");
  if (!inbox) return;
  (inbox as any).identity = CLINICAL_IDENTITY;

  onPagesEvent<{ workItemId: string }>(document, "work-item:selected", (payload) => {
    const items = (inbox as any).data ?? (inbox as any).items ?? [];
    const match = items.find((r: any) => r.item?.id === payload.workItemId);
    if (!match) return;
    const types: string[] = match.item.types ?? [];
    if (types.includes("adverse-event")) {
      window.location.hash = "#/page/Safety%20Workbench";
    } else if (types.includes("deviation-review")) {
      window.location.hash = "#/page/Protocol%20Workbench";
    }
  });
}

const container = document.getElementById("app");
if (container) {
  loadSite(container, app).then(() => {
    configureWorkItemInbox();
  }).catch((err) => {
    console.error("loadSite failed:", err);
    container.innerHTML = `<pre style="color:red;padding:2rem;">${err?.stack ?? err}</pre>`;
  });
}
