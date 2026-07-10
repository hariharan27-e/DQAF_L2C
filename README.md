# DQAF Validation Suite — Equinix L2C

Config-driven BigQuery data quality framework built on Cucumber + Java.
Add a new table to validate by adding a row to `tables_config.csv` — no code changes needed.

## What it checks

Each check runs as its **own Cucumber scenario**, looping over every table row in `tables_config.csv`:

| # | Scenario | What it does |
|---|----------|---------------|
| 1 | Row Count Reconciliation | Compares source vs target row count for `test_date` |
| 2 | Duplicate Key Check | Finds duplicate `key_column` values in the target table |
| 3 | Null Value Check | Checks configured `null_columns` for NULLs in the target table |
| 4 | Schema Check | Compares column names & types via `INFORMATION_SCHEMA.COLUMNS` |
| 5 | Sample Data Validation | Samples N rows from source, diffs them against target on `compare_columns` |
| 6 | Incremental Load Check | Checks the last 5 days ending at `test_date` (on `date_column`) -- confirms each day's new rows landed in target and match source |

At the end of the run, a **custom colored HTML report is generated per table**, plus an
index page linking to all of them:

```
target/dqaf-report/index.html                          -- overview + links to each table
target/dqaf-report/product_apigee_apps___APIGEE_APPS.html
target/dqaf-report/product_apigee_developers___APIGEE_DEVELOPERS.html
```

Each file is fully standalone (its own `<html>`, inline CSS, no shared dependencies) so
you can download, email, or share any single table's report on its own without the rest.
Every report -- the index and each per-table page -- has a header showing **Total Test
Cases / Passed / Failed / Errors** for whatever scope that page covers.

Each table page has one section per check with green/red/orange pass/fail/error pills, and
a **Sample Data** block listing the actual sampled rows for that table (from the Sample
Data Validation check).

The standard Cucumber report (`target/cucumber-reports/report.html`) and the
`maven-cucumber-reporting` plugin output (`target/cucumber-html-reports`) still generate too —
this is in addition to those, not a replacement.

## Project structure

```
dqaf-validation-suite/
├── pom.xml
├── src/test/resources/
│   ├── tables_config.csv
│   └── features/data_quality_suite.feature
└── src/test/java/
    ├── runners/TestRunner.java          -- Cucumber+JUnit entry point
    ├── stepdefinitions/
    │   ├── FullValidationSteps.java     -- all 6 check implementations
    │   └── Hooks.java                   -- fires report generation @AfterAll
    ├── context/TestContext.java         -- shared state across steps (singleton)
    ├── model/
    │   ├── TableConfig.java             -- one row of tables_config.csv
    │   └── CheckResult.java             -- one check outcome (+ sample rows)
    ├── utils/
    │   ├── ConfigLoader.java            -- reads tables_config.csv
    │   └── BigQueryService.java         -- all BigQuery SQL logic
    └── report/ReportGenerator.java      -- builds the custom colored HTML report
```

## tables_config.csv format

```csv
source_table,target_table,key_column,date_column,test_date,null_columns,sample_size,compare_columns
project.dataset.src_table,project.dataset.tgt_table,id,load_date,2026-07-01,id|email,20,name|status|email
```

- `source_table` / `target_table` — fully qualified `project.dataset.table`
- `key_column` — column used for duplicate checks and to join source/target for sample validation
- `date_column` / `test_date` — used by row count and incremental checks
- `null_columns` — columns to null-check in target. **Pipe-separated** for multiple, e.g. `id|email`
- `sample_size` — number of rows to sample for Sample Data Validation
- `compare_columns` — columns to diff between source & target samples. **Pipe-separated**. Leave blank to only check the sampled keys exist in target (no column-level diff)

> Note: pipe `|` is used (not comma) for multi-value fields because comma is the CSV column delimiter.

## Setup

1. **Auth to GCP** (one of):
   ```bash
   gcloud auth application-default login
   ```
   or set `GOOGLE_APPLICATION_CREDENTIALS` to a service account key with BigQuery read access
   to both source and target datasets/projects.

2. **Install dependencies**:
   ```bash
   mvn clean install -DskipTests
   ```

## Running

Run everything:
```bash
mvn test
```

Every run automatically archives all 3 reports into a timestamped folder so
past runs are never overwritten and survive `mvn clean`:

```
reports-archive/
├── 2026-07-09_153045/
│   ├── dqaf-report/               (the custom colored report)
│   ├── cucumber-reports/          (pretty report + raw json)
│   └── cucumber-html-reports/     (Masterthought dashboard)
└── 2026-07-09_161530/
    └── ...
```

No extra command needed -- this happens as part of `mvn test` itself.
To archive somewhere else (e.g. a shared drive), pass:
```bash
mvn test -Ddqaf.archive.dir="C:\path\to\somewhere\else"
```

The live (non-archived) copies are always also available straight after a run at:
```
target/dqaf-report/index.html
target/cucumber-reports/report.html
target/cucumber-html-reports/overview-features.html
```

Run a single scenario type (tag-free — just comment out the other Scenario blocks in the
`.feature` file, or use Cucumber's `--name` filter):
```bash
mvn test -Dcucumber.filter.name="Row Count Reconciliation"
```

After the run, open:
```
target/dqaf-report/index.html
```

## Extending

- **New table**: add a row to `tables_config.csv`. All 6 scenarios pick it up automatically.
- **New check type**: add a `CheckType` enum value in `CheckResult`, a step method in
  `FullValidationSteps`, a matching `When`/`Then` pair in the `.feature` file, and it will
  automatically get its own colored section in the report (grouped per table).
