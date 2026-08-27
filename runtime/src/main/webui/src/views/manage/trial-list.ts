import { page, rows, dataTable, title, lookup } from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";
import { schemaForm } from "../../forms.js";

export function trialList(): Component {
  return rows(
    title("Clinical Trials", "h2"),
    dataTable({
      title: "All Trials",
      lookup: lookup("trials"),
      sortable: true,
      pageSize: 25,
      columns: [
        { id: "protocolId" as never, name: "Protocol ID" },
        { id: "phase" as never, name: "Phase" },
        { id: "sponsor" as never, name: "Sponsor" },
        { id: "status" as never, name: "Status" },
        { id: "targetEnrollment" as never, name: "Target" },
      ],
      filter: { enabled: true },
    }),
    page("Register Trial",
      schemaForm({
        schema: {
          properties: {
            protocolId: { type: "string", minLength: 1 },
            phase: { type: "string", enum: ["EARLY_PHASE_I", "PHASE_I", "PHASE_I_II", "PHASE_II", "PHASE_II_III", "PHASE_III", "PHASE_IV"] },
            sponsor: { type: "string", minLength: 1 },
            targetEnrollment: { type: "integer", minimum: 1 },
          },
          required: ["protocolId", "phase", "sponsor", "targetEnrollment"],
        },
        labels: { protocolId: "Protocol ID", targetEnrollment: "Target Enrollment" },
        fieldOrder: ["protocolId", "phase", "sponsor", "targetEnrollment"],
        validateOnBlur: true,
        forceCreate: true,
      }),
      {
        dataScope: { dataset: "trials", idColumn: "id" },
        save: { trigger: "button", adapter: "rest" },
      },
    ),
  );
}
