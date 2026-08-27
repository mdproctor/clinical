import { bind, restSource } from "@casehubio/pages-ui";
import { mutableRestSource } from "@casehubio/pages-data";
import type { DataSourceBinding } from "@casehubio/pages-data";
import type { DataSetId } from "@casehubio/pages-data";

let currentTrialId: string | null = null;

export function setTrialId(id: string) { currentTrialId = id; }
export function getTrialId(): string | null { return currentTrialId; }

export function restDataset(id: string, url: string): DataSourceBinding {
  return bind(id, restSource(url));
}

export function mutableDataset(id: string, readUrl: string, createUrl: string, keyColumn = "id"): DataSourceBinding {
  return bind(id, mutableRestSource(readUrl, {
    create: { url: createUrl, method: "POST" },
    keyColumn,
    refreshAfterWrite: true,
  }), { keyColumn });
}

export const trialsDs = mutableDataset("trials", "/api/trials", "/api/trials");

export function trialDatasets(trialId: string): DataSourceBinding[] {
  return [
    restDataset("trial-summary", `/api/trials/${trialId}/summary`),
    mutableDataset("sites", `/api/trials/${trialId}/sites`, `/api/trials/${trialId}/sites`),
    restDataset("patients", `/api/trials/${trialId}/patients`),
    restDataset("adverse-events", `/api/trials/${trialId}/adverse-events`),
    restDataset("deviations", `/api/trials/${trialId}/deviations`),
    mutableDataset("amendments", `/api/trials/${trialId}/amendments`, `/api/trials/${trialId}/amendments`),
    restDataset("agents", `/api/trials/${trialId}/agents`),
    restDataset("ledger-entries", `/api/trials/${trialId}/ledger-entries`),
    restDataset("work-items", "/api/workitems?candidateGroups=clinical"),
  ];
}

export function patientDatasets(trialId: string, siteId: string, enrollmentId: string): DataSourceBinding[] {
  const base = `/api/trials/${trialId}/sites/${siteId}/patients/${enrollmentId}`;
  return [
    mutableDataset("patient-visits", `${base}/visits`, `${base}/visits`),
    mutableDataset("patient-lab-results", `${base}/lab-results`, `${base}/lab-results`),
    mutableDataset("patient-vitals", `${base}/vitals`, `${base}/vitals`),
    mutableDataset("patient-medications", `${base}/medications`, `${base}/medications`),
    mutableDataset("patient-study-drug", `${base}/study-drug`, `${base}/study-drug`),
    mutableDataset("patient-adverse-events", `${base}/adverse-events`, `${base}/adverse-events`),
  ];
}
