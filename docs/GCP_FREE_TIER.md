# GCP Cloud Run configuration

The repository includes [`cloudrun.yaml`](../cloudrun.yaml) as a starting point for deploying the
Spring Boot service to Cloud Run. This is configuration only: no GCP project was accessed, no
billing was enabled, and no resources were deployed.

## Cost boundary

Cloud Run's free tier is a monthly usage allowance, not a guarantee that a deployment cannot incur
charges. As checked on 2026-11-11, Google lists the following request-based Cloud Run allowances
per billing account (using `us-central1` pricing): 180,000 vCPU-seconds, 360,000 GiB-seconds, and
2 million requests per month. Usage beyond the allowance can be billed.

The manifest limits the service to one instance at both the service and revision levels, uses
512 MiB of memory, and does not configure minimum instances (Cloud Run defaults to zero). These
are cost-reduction measures, not a hard cap: Google notes that a maximum-instance limit can be
exceeded briefly in some circumstances. Budgets that only send alerts do not stop usage. Cloud
Build, Artifact Registry storage, network egress, and any separately hosted database or AI APIs
have their own pricing and allowances.

Google Cloud documents spend caps for eligible Cloud Run resources, but that does not make the
entire project free of charge: persistent resources can continue accruing costs, and in-flight
requests can finish after a cap is triggered. Check the current eligibility and limitations before
relying on a spend cap.

Sources:

- [Cloud Run pricing and free tier](https://cloud.google.com/run/pricing)
- [Cloud Run maximum instances](https://cloud.google.com/run/docs/configuring/max-instances)
- [Cloud Billing budgets and spend caps](https://cloud.google.com/billing/docs/how-to/budgets-spend-caps)

## Included runtime defaults

- The service uses MongoDB Atlas for persistent incident storage, configured through the
  `incident-copilot-mongodb-uri` Secret Manager secret. Chat and embeddings remain in offline mode:
  no LLM or embedding API keys are configured.
- The existing Atlas M0 cluster is a separate provider and has its own limits. The screenshot you
  shared showed 142.66 MB of 512 MB in use; check current usage in Atlas before adding data.
- The service is configured for one vCPU, 512 MiB RAM, a concurrency of eight, and a 300-second
  request timeout. The Docker image's JVM heap is already limited for a 512 MiB container.
- `CORS_ALLOWED_ORIGINS` is set to the frontend origin documented by this project. CORS is not
  authentication; keep the Cloud Run service private until you deliberately set up API
  authentication and abuse protection.

## Atlas setup required before deployment

1. In Atlas, create a dedicated database user for this app with access only to the
   `incident_copilot` database. Use a strong, unique password. Do not use an Atlas organization
   owner credential.
2. Create a Secret Manager secret named `incident-copilot-mongodb-uri` and add the Atlas driver
   URI as its secret value. Use the `incident_copilot` database in the URI. Do not put the URI in
   source control, deployment YAML, chat, or shell history. The manifest references secret version
   `1`; if you rotate the URI, update the manifest to the new numbered version and redeploy.
3. Configure the Cloud Run service identity to have permission to access that secret. The manifest
   references numbered version `1`. Do not grant the service identity broad project permissions.
4. Configure Atlas Network Access. The Atlas screenshot shared on 2026-11-11 shows an active
   `0.0.0.0/0` IP access-list entry. This permits connection attempts from any internet address;
   database authentication and TLS are still required, but this is broader exposure than needed.
   Remove that rule in Atlas if no existing client depends on it. The separate `/32` entry shown
   for your current IP only covers that client and will not allow Cloud Run.

   Cloud Run does not provide a fixed outbound IP by default, so adding one exact Cloud Run source
   address to the Atlas IP access list is not possible with this template. A fixed egress IP
   typically requires additional networking resources that can cost money. Do not deploy until
   you have chosen and reviewed an acceptable networking approach. Avoid adding `0.0.0.0/0` as a
   workaround.
5. In Atlas Search, create and wait for the `incident_vector_index` Vector Search index on database
   `incident_copilot`, collection `incidents`, using the definition in the README. Its vector
   dimensions must match `EMBEDDING_DIMENSIONS` (768 by default).
6. Confirm the Atlas M0 cluster has enough remaining storage for the incidents you intend to add.
   The bundled seed data is small, but the free storage quota is shared with any data already in
   the cluster.

Atlas persistence does not make the offline analyst or local embeddings use Gemini or another AI
provider. Those remain offline unless API keys are separately configured, and third-party API usage
has independent quotas and possible charges.

## Before any deployment

No deploy command has been run. A future deployment would require a GCP project and appropriate
Cloud Run, Cloud Build, and Artifact Registry setup, which can require a linked billing account.
Before proceeding:

1. Review the current Cloud Run free-tier quota and every other resource that the deployment would
   create. Do not treat the free tier or budget alerts as a zero-cost guarantee.
2. Complete the Atlas setup above, including the network access decision and vector index.
3. Replace the image placeholder in `cloudrun.yaml` with an image that has already been built and
   stored in Artifact Registry. Building and retaining that image can have separate costs.
4. Keep the service private unless you have deliberately addressed authentication and abuse
   protection for the API.
5. If you require a strict no-overage boundary, do not enable billing or deploy this service to
   Cloud Run. This repository configuration cannot enforce a whole-account spending cap.

When deployment is explicitly approved and costs have been reviewed, Cloud Run accepts this
manifest with `gcloud run services replace cloudrun.yaml`. That command was not run.
