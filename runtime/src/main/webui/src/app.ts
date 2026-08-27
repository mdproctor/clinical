import { page, tree, tabs } from "@casehubio/pages-ui";
import { trialsDs, trialDatasets, patientDatasets } from "./datasets.js";
import { trialList } from "./views/manage/trial-list.js";
import { trialDetail } from "./views/manage/trial-detail.js";
import { patientDetail } from "./views/manage/patient-detail.js";
import { workQueue } from "./views/work-queue.js";
import { safetyWorkbench } from "./views/safety-workbench.js";
import { protocolWorkbench } from "./views/protocol-workbench.js";
import { operations } from "./views/operations.js";

const DEFAULT_TRIAL_ID = "316e3846-4ea7-3b18-a6f7-e01ce6582a69";
const params = new URLSearchParams(window.location.search);
const trialId = params.get("trialId") || DEFAULT_TRIAL_ID;
const siteId = params.get("siteId") || "";
const enrollmentId = params.get("enrollmentId") || "";

export const app = page(
  "CaseHub Clinical",
  tree(
    ["Manage", page("Manage",
      tabs(
        ["Trials", trialList()],
        ["Trial Detail", trialDetail(trialId)],
        ...(siteId && enrollmentId
          ? [["Patient Detail", patientDetail(trialId, siteId, enrollmentId)] as [string, ReturnType<typeof patientDetail>]]
          : []),
      ),
    )],
    ["Review", page("Review",
      tree(
        ["Work Queue", workQueue()],
        ["Safety Workbench", safetyWorkbench()],
        ["Protocol Workbench", protocolWorkbench()],
        ["Operations", operations()],
      ),
    )],
  ),
  {
    datasets: [
      trialsDs,
      ...trialDatasets(trialId),
      ...(siteId && enrollmentId ? patientDatasets(trialId, siteId, enrollmentId) : []),
    ],
  },
);
