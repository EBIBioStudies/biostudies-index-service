This page provides a high-level overview of the collections defined in the registry.  
For the full schema structure and field-level configuration, see [Collections Registry](collections-registry.md).

## `public`

The `public` collection contains shared fields that apply across submissions.

| Collection | Purpose | Example fields |
| --- | --- | --- |
| `public` | Shared indexing and retrieval fields | `access`, `accession`, `type`, `title`, `author`, `content`, `links`, `files`, `release_date` |

## `idr`

| Collection | Purpose | Example fields |
| --- | --- | --- |
| `idr` | IDR-specific metadata | `facet.idr.study_type` |

## `hecatos`

| Collection | Purpose | Example fields |
| --- | --- | --- |
| `hecatos` | Hecatos metadata | `facet.hecatos.organ`, `facet.hecatos.technology`, `facet.hecatos.data_type`, `facet.hecatos.compound`, `facet.hecatos.raw_or_processed` |

## `arrayexpress`

| Collection | Purpose | Example fields |
| --- | --- | --- |
| `arrayexpress` | ArrayExpress submission metadata | `study_type`, `organism`, `technology`, `experimental_design`, `assay_count`, `sample_count`, `facet.*` fields |

## `biomodels`

| Collection | Purpose | Example fields |
| --- | --- | --- |
| `biomodels` | BioModels metadata | `biomodels.domain`, `biomodels.curation_status`, `biomodels.modelling_approach`, `biomodels.model_format`, `biomodels.model_tags`, `biomodels.organism` |

## `europepmc`

| Collection | Purpose | Example fields |
| --- | --- | --- |
| `europepmc` | Europe PMC-specific metadata | `facet.europepmc.funding_agency` |

## `eu-toxrisk`

| Collection | Purpose | Example fields |
| --- | --- | --- |
| `eu-toxrisk` | EUToxRisk metadata | `facet.eutoxrisk.data_type`, `facet.eutoxrisk.organ`, `facet.eutoxrisk.organism`, `facet.eutoxrisk.toxicity_domain`, `facet.eutoxrisk.compound`, `facet.eutoxrisk.method_name`, `facet.eutoxrisk.project_part` |

## `rh3r`

| Collection | Purpose | Example fields |
| --- | --- | --- |
| `rh3r` | RH3R metadata | `facet.rh3r.wrk_pckg`, `facet.rh3r.case_study`, `facet.rh3r.info_type`, `facet.rh3r.organ`, `facet.rh3r.toxy_dom`, `facet.rh3r.org` |

## `cancermodelsorg`

| Collection | Purpose | Example fields |
| --- | --- | --- |
| `cancermodelsorg` | CancerModels.org metadata | `facet.cancermodelsorg.model_id`, `facet.cancermodelsorg.model_type`, `facet.cancermodelsorg.dataset_available`, `facet.cancermodelsorg.model_availability`, `facet.cancermodelsorg.related_models`, `facet.cancermodelsorg.project`, `facet.cancermodelsorg.paediatric`, `facet.cancermodelsorg.patient_sex`, `facet.cancermodelsorg.tumour_type`, `facet.cancermodelsorg.patient_ethnicity_group`, `facet.cancermodelsorg.patient_age`, `facet.cancermodelsorg.cancer_system`, `facet.cancermodelsorg.breast_cancer_biomarkers`, `facet.cancermodelsorg.msi_status`, `facet.cancermodelsorg.hla_types`, `facet.cancermodelsorg.patient_treatment_response`, `facet.cancermodelsorg.model_treatment_response` |

## Notes

- `public` provides the shared baseline fields used across the system.
- Some collections are facet-only, while others include both searchable and retrieved fields.
- A few properties use custom parsers or analyzers to normalize values before indexing.
- For field-level details, see [Collections Registry](collections-registry.md).