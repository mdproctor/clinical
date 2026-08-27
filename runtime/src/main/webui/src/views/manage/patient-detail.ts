import { page, rows, tabs, dataTable, title, html, lookup } from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";
import { schemaForm } from "../../forms.js";

export function patientDetail(trialId: string, siteId: string, enrollmentId: string): Component {
  const base = `/api/trials/${trialId}/sites/${siteId}/patients/${enrollmentId}`;

  const enrollment = rows(
    title("Patient Enrollment", "h3"),
    html(`<div id="enrollment-card" style="padding:1rem;border:1px solid var(--pages-neutral-6,#ddd);border-radius:var(--pages-radius-sm,4px);">Loading...</div>
      <script>
        (async () => {
          const resp = await fetch('${base}');
          const card = document.getElementById('enrollment-card');
          if (resp.ok) {
            const e = await resp.json();
            card.innerHTML = '<dl style="display:grid;grid-template-columns:auto 1fr;gap:0.25rem 1rem;">'
              + '<dt>Patient ID</dt><dd>'+e.patientId+'</dd>'
              + '<dt>Status</dt><dd>'+e.enrollmentStatus+'</dd>'
              + '<dt>Consent</dt><dd>'+e.consentStatus+'</dd>'
              + '</dl>';
          } else card.textContent = 'Failed to load enrollment';
        })();
      </script>
      <div style="margin-top:1rem;">
        <button onclick="if(confirm('Withdraw consent? This is irreversible.'))fetch('${base}/withdraw-consent',{method:'POST'}).then(r=>{if(r.ok)window.location.reload();else if(r.status===409)alert('Already withdrawn');else alert('Failed: '+r.status)})"
          style="padding:0.5rem 1.5rem;background:var(--pages-red-9,#dc2626);color:white;border:none;border-radius:var(--pages-radius-sm,4px);cursor:pointer;">
          Withdraw Consent
        </button>
      </div>`),
  );

  const adverseEvents = rows(
    dataTable({
      title: "Adverse Events",
      lookup: lookup("patient-adverse-events"),
      sortable: true,
      columns: [
        { id: "grade" as never, name: "Grade" },
        { id: "actuality" as never, name: "Actuality" },
        { id: "occurredAt" as never, name: "Occurred", expression: 'value ? $substring(value, 0, 10) : ""' },
        { id: "escalationStatus" as never, name: "Escalation" },
      ],
    }),
    page("Report Adverse Event",
      schemaForm({
        schema: {
          properties: {
            grade: { type: "string", enum: ["GRADE_1", "GRADE_2", "GRADE_3", "GRADE_4", "GRADE_5"] },
            occurredAt: { type: "string", format: "date" },
            actuality: { type: "string", enum: ["ACTUAL", "POTENTIAL"] },
            unexpected: { type: "boolean" },
            suspected: { type: "boolean" },
          },
          required: ["grade", "occurredAt"],
        },
        labels: { occurredAt: "Occurred At" },
        fieldOrder: ["grade", "occurredAt", "actuality", "unexpected", "suspected"],
        validateOnBlur: true,
        forceCreate: true,
      }),
      {
        dataScope: { dataset: "patient-adverse-events", idColumn: "id" },
        save: { trigger: "button", adapter: "rest" },
      },
    ),
  );

  const visits = rows(
    dataTable({
      title: "Visits",
      lookup: lookup("patient-visits"),
      sortable: true,
      columns: [
        { id: "visitType" as never, name: "Type" },
        { id: "visitDate" as never, name: "Date", expression: 'value ? $substring(value, 0, 10) : ""' },
        { id: "status" as never, name: "Status" },
        { id: "notes" as never, name: "Notes", expression: 'value ? ($length(value) > 40 ? $substring(value, 0, 40) & "..." : value) : ""' },
      ],
    }),
    page("Schedule Visit",
      schemaForm({
        schema: {
          properties: {
            visitType: { type: "string", enum: ["SCREENING", "BASELINE", "FOLLOW_UP", "UNSCHEDULED", "END_OF_STUDY"] },
            visitDate: { type: "string", format: "date" },
            status: { type: "string", enum: ["SCHEDULED", "COMPLETED", "MISSED", "CANCELLED"] },
            notes: { type: "string", format: "textarea" },
          },
          required: ["visitType", "visitDate", "status"],
        },
        labels: { visitType: "Visit Type", visitDate: "Visit Date" },
        fieldOrder: ["visitType", "visitDate", "status", "notes"],
        validateOnBlur: true,
        forceCreate: true,
      }),
      {
        dataScope: { dataset: "patient-visits", idColumn: "id" },
        save: { trigger: "button", adapter: "rest" },
      },
    ),
  );

  const labResults = rows(
    dataTable({
      title: "Lab Results",
      lookup: lookup("patient-lab-results"),
      sortable: true,
      columns: [
        { id: "testName" as never, name: "Test" },
        { id: "value" as never, name: "Value" },
        { id: "unit" as never, name: "Unit" },
        { id: "abnormalFlag" as never, name: "Flag" },
        { id: "specimenType" as never, name: "Specimen" },
        { id: "collectedAt" as never, name: "Collected", expression: 'value ? $substring(value, 0, 10) : ""' },
      ],
    }),
    page("Record Lab Result",
      schemaForm({
        schema: {
          properties: {
            testName: { type: "string", minLength: 1 },
            value: { type: "number" },
            unit: { type: "string", minLength: 1 },
            referenceRangeLow: { type: "number" },
            referenceRangeHigh: { type: "number" },
            abnormalFlag: { type: "string", enum: ["NORMAL", "LOW", "HIGH", "CRITICAL_LOW", "CRITICAL_HIGH"] },
            specimenType: { type: "string", enum: ["BLOOD", "URINE", "CSF", "TISSUE"] },
            performingLab: { type: "string" },
            collectedAt: { type: "string", format: "date" },
          },
          required: ["testName", "value", "unit", "abnormalFlag", "specimenType", "collectedAt"],
        },
        labels: {
          testName: "Test Name", referenceRangeLow: "Ref Range Low",
          referenceRangeHigh: "Ref Range High", abnormalFlag: "Abnormal Flag",
          specimenType: "Specimen Type", performingLab: "Performing Lab",
          collectedAt: "Collected At",
        },
        fieldOrder: ["testName", "value", "unit", "referenceRangeLow", "referenceRangeHigh",
                     "abnormalFlag", "specimenType", "performingLab", "collectedAt"],
        validateOnBlur: true,
        forceCreate: true,
      }),
      {
        dataScope: { dataset: "patient-lab-results", idColumn: "id" },
        save: { trigger: "button", adapter: "rest" },
      },
    ),
  );

  const vitals = rows(
    dataTable({
      title: "Vital Signs",
      lookup: lookup("patient-vitals"),
      sortable: true,
      columns: [
        { id: "type" as never, name: "Type" },
        { id: "value" as never, name: "Value" },
        { id: "unit" as never, name: "Unit" },
        { id: "measuredAt" as never, name: "Measured", expression: 'value ? $substring(value, 0, 16) : ""' },
      ],
    }),
    page("Record Vital Sign",
      schemaForm({
        schema: {
          properties: {
            type: { type: "string", enum: ["HEART_RATE", "BP_SYSTOLIC", "BP_DIASTOLIC", "TEMPERATURE", "RESPIRATORY_RATE", "O2_SATURATION", "WEIGHT", "HEIGHT"] },
            value: { type: "number" },
            unit: { type: "string", minLength: 1 },
            measuredAt: { type: "string", format: "date" },
          },
          required: ["type", "value", "unit", "measuredAt"],
        },
        labels: { measuredAt: "Measured At" },
        fieldOrder: ["type", "value", "unit", "measuredAt"],
        validateOnBlur: true,
        forceCreate: true,
      }),
      {
        dataScope: { dataset: "patient-vitals", idColumn: "id" },
        save: { trigger: "button", adapter: "rest" },
      },
    ),
  );

  const medications = rows(
    dataTable({
      title: "Concomitant Medications",
      lookup: lookup("patient-medications"),
      sortable: true,
      columns: [
        { id: "medicationName" as never, name: "Name" },
        { id: "dose" as never, name: "Dose" },
        { id: "route" as never, name: "Route" },
        { id: "frequency" as never, name: "Frequency" },
        { id: "startDate" as never, name: "Start" },
        { id: "ongoing" as never, name: "Ongoing" },
      ],
    }),
    page("Record Medication",
      schemaForm({
        schema: {
          properties: {
            medicationName: { type: "string", minLength: 1 },
            indication: { type: "string" },
            dose: { type: "string", minLength: 1 },
            unit: { type: "string", minLength: 1 },
            route: { type: "string", enum: ["ORAL", "IV", "IM", "SC", "TOPICAL", "INHALED"] },
            frequency: { type: "string", enum: ["ONCE_DAILY", "TWICE_DAILY", "THREE_TIMES_DAILY", "FOUR_TIMES_DAILY", "AS_NEEDED", "WEEKLY"] },
            startDate: { type: "string", format: "date" },
            ongoing: { type: "boolean" },
          },
          required: ["medicationName", "dose", "unit", "route", "frequency", "startDate"],
        },
        labels: { medicationName: "Medication Name", startDate: "Start Date" },
        fieldOrder: ["medicationName", "indication", "dose", "unit", "route", "frequency", "startDate", "ongoing"],
        validateOnBlur: true,
        forceCreate: true,
      }),
      {
        dataScope: { dataset: "patient-medications", idColumn: "id" },
        save: { trigger: "button", adapter: "rest" },
      },
    ),
  );

  const studyDrug = rows(
    dataTable({
      title: "Study Drug Administrations",
      lookup: lookup("patient-study-drug"),
      sortable: true,
      columns: [
        { id: "drugName" as never, name: "Drug" },
        { id: "dose" as never, name: "Dose" },
        { id: "route" as never, name: "Route" },
        { id: "administeredAt" as never, name: "Administered", expression: 'value ? $substring(value, 0, 16) : ""' },
        { id: "administeredBy" as never, name: "By" },
        { id: "status" as never, name: "Status" },
      ],
    }),
    page("Record Administration",
      schemaForm({
        schema: {
          properties: {
            drugName: { type: "string", minLength: 1 },
            dose: { type: "string", minLength: 1 },
            unit: { type: "string", minLength: 1 },
            route: { type: "string", enum: ["ORAL", "IV", "IM", "SC", "TOPICAL", "INHALED"] },
            administeredAt: { type: "string", format: "date" },
            administeredBy: { type: "string", minLength: 1 },
            batchNumber: { type: "string" },
            status: { type: "string", enum: ["ADMINISTERED", "HELD", "DISCONTINUED", "DOSE_MODIFIED"] },
          },
          required: ["drugName", "dose", "unit", "route", "administeredAt", "administeredBy", "status"],
        },
        labels: {
          drugName: "Drug Name", administeredAt: "Administered At",
          administeredBy: "Administered By", batchNumber: "Batch Number",
        },
        fieldOrder: ["drugName", "dose", "unit", "route", "administeredAt", "administeredBy", "batchNumber", "status"],
        validateOnBlur: true,
        forceCreate: true,
      }),
      {
        dataScope: { dataset: "patient-study-drug", idColumn: "id" },
        save: { trigger: "button", adapter: "rest" },
      },
    ),
  );

  return tabs(
    ["Enrollment", enrollment],
    ["Adverse Events", adverseEvents],
    ["Visits", visits],
    ["Lab Results", labResults],
    ["Vitals", vitals],
    ["Medications", medications],
    ["Study Drug", studyDrug],
  );
}
