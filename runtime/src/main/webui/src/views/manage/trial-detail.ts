import {
  page, rows, columns, tabs, dataTable, metric, title, html, lookup, groupBy, col,
} from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";
import { schemaForm } from "../../forms.js";

export function trialDetail(trialId: string): Component {
  const overview = rows(
    columns([3, 3, 3, 3],
      [metric({ title: "Phase", lookup: lookup("trial-summary", groupBy(null, col("phase"))) })],
      [metric({ title: "Enrolled", lookup: lookup("trial-summary", groupBy(null, col("totalEnrolled"))) })],
      [metric({ title: "Adverse Events", lookup: lookup("trial-summary", groupBy(null, col("totalAdverseEvents"))) })],
      [metric({ title: "Deviations", lookup: lookup("trial-summary", groupBy(null, col("totalDeviations"))) })],
    ),
    html(`<div style="margin-top:1rem;display:flex;gap:0.75rem;">
      <button onclick="fetch('/api/trials/${trialId}/activate',{method:'POST'}).then(r=>{if(r.ok)window.location.reload();else alert('Failed: '+r.status)})"
        style="padding:0.5rem 1.5rem;background:var(--pages-accent-9,#5470c6);color:white;border:none;border-radius:var(--pages-radius-sm,4px);cursor:pointer;">
        Activate Trial
      </button>
    </div>`),
  );

  const sites = rows(
    dataTable({
      title: "Sites",
      lookup: lookup("sites"),
      sortable: true,
      columns: [
        { id: "investigatorId" as never, name: "Investigator" },
        { id: "targetEnrollment" as never, name: "Target" },
        { id: "enrolledCount" as never, name: "Enrolled" },
        { id: "status" as never, name: "Status" },
        { id: "aeCount" as never, name: "AEs" },
      ],
    }),
    page("Add Site",
      schemaForm({
        schema: {
          properties: {
            investigatorId: { type: "string", minLength: 1 },
            targetEnrollment: { type: "integer", minimum: 1 },
          },
          required: ["investigatorId"],
        },
        labels: { investigatorId: "Investigator ID", targetEnrollment: "Target Enrollment" },
        validateOnBlur: true,
        forceCreate: true,
      }),
      {
        dataScope: { dataset: "sites", idColumn: "id" },
        save: { trigger: "button", adapter: "rest" },
      },
    ),
  );

  const patients = rows(
    dataTable({
      title: "Patients",
      lookup: lookup("patients"),
      sortable: true,
      pageSize: 25,
      columns: [
        { id: "patientId" as never, name: "Patient ID" },
        { id: "siteName" as never, name: "Site" },
        { id: "enrollmentStatus" as never, name: "Status" },
        { id: "consentStatus" as never, name: "Consent" },
      ],
      filter: { enabled: true },
    }),
    html(`<div style="margin-top:1rem;padding:1rem;border:1px solid var(--pages-neutral-6,#ddd);border-radius:var(--pages-radius-sm,4px);">
      <h3 style="margin:0 0 0.75rem 0;">Enroll Patient</h3>
      <form id="enroll-patient-form" onsubmit="return false;">
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:0.75rem;">
          <label>Site<br/><select name="siteId" id="enroll-site-select" required style="width:100%;padding:0.4rem;"><option value="">Loading...</option></select></label>
          <label>Patient ID<br/><input name="patientId" required style="width:100%;padding:0.4rem;"/></label>
        </div>
        <button type="submit" style="margin-top:0.75rem;padding:0.5rem 1.5rem;background:var(--pages-accent-9,#5470c6);color:white;border:none;border-radius:var(--pages-radius-sm,4px);cursor:pointer;">Enroll</button>
      </form>
      <script>
        (async () => {
          const sel = document.getElementById('enroll-site-select');
          const resp = await fetch('/api/trials/${trialId}/sites');
          if (resp.ok) {
            const sites = await resp.json();
            sel.innerHTML = sites.map(s => '<option value="'+s.id+'">'+s.investigatorId+'</option>').join('');
          }
        })();
        document.getElementById('enroll-patient-form')?.addEventListener('submit', async () => {
          const form = document.getElementById('enroll-patient-form');
          const fd = new FormData(form);
          const siteId = fd.get('siteId');
          const resp = await fetch('/api/trials/${trialId}/sites/'+siteId+'/patients', {
            method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({patientId: fd.get('patientId')})
          });
          if (resp.ok) { form.reset(); window.location.reload(); } else alert('Failed: '+resp.status);
        });
      </script>
    </div>`),
  );

  const deviations = rows(
    dataTable({
      title: "Protocol Deviations",
      lookup: lookup("deviations"),
      sortable: true,
      columns: [
        { id: "deviationType" as never, name: "Type" },
        { id: "severity" as never, name: "Severity" },
        { id: "siteName" as never, name: "Site" },
        { id: "piApprovalStatus" as never, name: "PI Approval" },
        { id: "irbDecision" as never, name: "IRB" },
        { id: "reportedAt" as never, name: "Reported", expression: 'value ? $substring(value, 0, 10) : ""' },
      ],
    }),
    html(`<div style="margin-top:1rem;padding:1rem;border:1px solid var(--pages-neutral-6,#ddd);border-radius:var(--pages-radius-sm,4px);">
      <h3 style="margin:0 0 0.75rem 0;">Report Deviation</h3>
      <form id="report-deviation-form" onsubmit="return false;">
        <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:0.75rem;">
          <label>Site<br/><select name="siteId" id="dev-site-select" required style="width:100%;padding:0.4rem;"><option value="">Loading...</option></select></label>
          <label>Type<br/><input name="deviationType" required style="width:100%;padding:0.4rem;"/></label>
          <label>Severity<br/><select name="severity" required style="width:100%;padding:0.4rem;">
            <option value="MINOR">Minor</option><option value="MAJOR">Major</option><option value="CRITICAL">Critical</option>
          </select></label>
        </div>
        <button type="submit" style="margin-top:0.75rem;padding:0.5rem 1.5rem;background:var(--pages-accent-9,#5470c6);color:white;border:none;border-radius:var(--pages-radius-sm,4px);cursor:pointer;">Report</button>
      </form>
      <script>
        (async () => {
          const sel = document.getElementById('dev-site-select');
          const resp = await fetch('/api/trials/${trialId}/sites');
          if (resp.ok) {
            const sites = await resp.json();
            sel.innerHTML = sites.map(s => '<option value="'+s.id+'">'+s.investigatorId+'</option>').join('');
          }
        })();
        document.getElementById('report-deviation-form')?.addEventListener('submit', async () => {
          const form = document.getElementById('report-deviation-form');
          const fd = new FormData(form);
          const siteId = fd.get('siteId');
          const resp = await fetch('/api/trials/${trialId}/sites/'+siteId+'/deviations', {
            method:'POST', headers:{'Content-Type':'application/json'},
            body:JSON.stringify({deviationType: fd.get('deviationType'), severity: fd.get('severity')})
          });
          if (resp.ok) { form.reset(); window.location.reload(); } else alert('Failed: '+resp.status);
        });
      </script>
    </div>`),
  );

  const amendments = rows(
    dataTable({
      title: "Protocol Amendments",
      lookup: lookup("amendments"),
      sortable: true,
      columns: [
        { id: "proposedChange" as never, name: "Proposed Change", expression: 'value ? ($length(value) > 80 ? $substring(value, 0, 80) & "..." : value) : ""' },
        { id: "status" as never, name: "Status" },
        { id: "proposedAt" as never, name: "Proposed", expression: 'value ? $substring(value, 0, 10) : ""' },
      ],
    }),
    page("Propose Amendment",
      schemaForm({
        schema: {
          properties: {
            proposedChange: { type: "string", format: "textarea", minLength: 1 },
          },
          required: ["proposedChange"],
        },
        labels: { proposedChange: "Proposed Change" },
        validateOnBlur: true,
        forceCreate: true,
      }),
      {
        dataScope: { dataset: "amendments", idColumn: "id" },
        save: { trigger: "button", adapter: "rest" },
      },
    ),
  );

  return tabs(
    ["Overview", overview],
    ["Sites", sites],
    ["Patients", patients],
    ["Deviations", deviations],
    ["Amendments", amendments],
  );
}
