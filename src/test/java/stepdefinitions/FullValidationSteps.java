package stepdefinitions;

import context.TestContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import model.CheckResult;
import model.TableConfig;
import utils.BigQueryService;
import utils.ConfigLoader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FullValidationSteps {

    private final TestContext ctx = TestContext.getInstance();

    // -----------------------------------------------------------------
    // Config loading
    // -----------------------------------------------------------------

    @Given("I load table configurations from {string}")
    public void i_load_table_configurations_from(String fileName) throws Exception {
        List<TableConfig> configs = ConfigLoader.load(fileName);
        ctx.setTableConfigs(configs);
        System.out.println("Loaded " + configs.size() + " table configuration(s) from " + fileName);
    }

    /**
     * Lazily resolves the shared BigQuery client. Not built until a check actually
     * needs it (i.e. after the config has been loaded), and falls back to the
     * project ID embedded in the first configured source table if no
     * GOOGLE_CLOUD_PROJECT / -Dbq.project override is set.
     */
    private BigQueryService bq() {
        String fallbackProjectId = null;
        if (!ctx.getTableConfigs().isEmpty()) {
            String src = ctx.getTableConfigs().get(0).getSourceTable();
            String[] parts = src.split("\\.");
            if (parts.length > 0) {
                fallbackProjectId = parts[0];
            }
        }
        return BigQueryService.getInstance(fallbackProjectId);
    }

    // -----------------------------------------------------------------
    // 1. Row Count
    // -----------------------------------------------------------------

    @When("I run row count checks for every configured table")
    public void i_run_row_count_checks() {
        for (TableConfig cfg : ctx.getTableConfigs()) {
            CheckResult result = new CheckResult(cfg.getTableLabel(), CheckResult.CheckType.ROW_COUNT);
            try {
                // Full table count -- no date filtering here (that's what the
                // Incremental Load Check scenario is for).
                long sourceCount = bq().getRowCount(cfg.getSourceTable(), null, null);
                long targetCount = bq().getRowCount(cfg.getTargetTable(), null, null);
                result.addDetail("Source count: " + sourceCount);
                result.addDetail("Target count: " + targetCount);
                if (sourceCount == targetCount) {
                    result.pass("Full row counts match (" + sourceCount + " rows)");
                } else {
                    long diff = sourceCount - targetCount;
                    result.fail("Row count mismatch: source=" + sourceCount + ", target=" + targetCount +
                            " (diff=" + diff + ")");
                }
            } catch (Exception e) {
                result.error("Row count check failed to execute: " + e.getMessage());
            }
            ctx.addResult(result);
        }
    }

    @Then("all row count checks should have passed")
    public void all_row_count_checks_should_have_passed() {
        assertAllPassed(CheckResult.CheckType.ROW_COUNT);
    }

    // -----------------------------------------------------------------
    // 2. Duplicate Check
    // -----------------------------------------------------------------

    @When("I run duplicate checks for every configured table")
    public void i_run_duplicate_checks() {
        for (TableConfig cfg : ctx.getTableConfigs()) {
            CheckResult result = new CheckResult(cfg.getTableLabel(), CheckResult.CheckType.DUPLICATE);
            try {
                List<Map<String, Object>> targetDupes = bq().findDuplicates(cfg.getTargetTable(), cfg.getKeyColumn(), 20);
                if (targetDupes.isEmpty()) {
                    result.pass("No duplicate " + cfg.getKeyColumn() + " values found in target table");
                } else {
                    result.fail(targetDupes.size() + " duplicate key value(s) found in target on column '" +
                            cfg.getKeyColumn() + "'");
                    for (Map<String, Object> dup : targetDupes) {
                        result.addDetail(dup.get(cfg.getKeyColumn()) + " occurs " + dup.get("occurrences") + " times");
                    }
                }
            } catch (Exception e) {
                result.error("Duplicate check failed to execute: " + e.getMessage());
            }
            ctx.addResult(result);
        }
    }

    @Then("all duplicate checks should have passed")
    public void all_duplicate_checks_should_have_passed() {
        assertAllPassed(CheckResult.CheckType.DUPLICATE);
    }

    // -----------------------------------------------------------------
    // 3. Null Check
    // -----------------------------------------------------------------

    @When("I run null checks for every configured table")
    public void i_run_null_checks() {
        for (TableConfig cfg : ctx.getTableConfigs()) {
            CheckResult result = new CheckResult(cfg.getTableLabel(), CheckResult.CheckType.NULL_CHECK);
            try {
                if (cfg.getNullColumns().isEmpty()) {
                    result.pass("No null_columns configured -- skipped");
                    ctx.addResult(result);
                    continue;
                }
                Map<String, Long> nullCounts = bq().getNullCounts(cfg.getTargetTable(), cfg.getNullColumns());
                boolean anyNulls = nullCounts.values().stream().anyMatch(v -> v > 0);
                for (Map.Entry<String, Long> entry : nullCounts.entrySet()) {
                    result.addDetail(entry.getKey() + ": " + entry.getValue() + " null value(s)");
                }
                if (!anyNulls) {
                    result.pass("No nulls found in required column(s): " + String.join(", ", cfg.getNullColumns()));
                } else {
                    result.fail("Null values found in one or more required columns");
                }
            } catch (Exception e) {
                result.error("Null check failed to execute: " + e.getMessage());
            }
            ctx.addResult(result);
        }
    }

    @Then("all null checks should have passed")
    public void all_null_checks_should_have_passed() {
        assertAllPassed(CheckResult.CheckType.NULL_CHECK);
    }

    // -----------------------------------------------------------------
    // 4. Schema Check
    // -----------------------------------------------------------------

    @When("I run schema checks for every configured table")
    public void i_run_schema_checks() {
        for (TableConfig cfg : ctx.getTableConfigs()) {
            CheckResult result = new CheckResult(cfg.getTableLabel(), CheckResult.CheckType.SCHEMA);
            try {
                Map<String, String> sourceSchema = bq().getSchema(cfg.getSourceTable());
                Map<String, String> targetSchema = bq().getSchema(cfg.getTargetTable());

                List<String> mismatches = new ArrayList<>();
                for (Map.Entry<String, String> entry : sourceSchema.entrySet()) {
                    String col = entry.getKey();
                    String srcType = entry.getValue();
                    if (!targetSchema.containsKey(col)) {
                        mismatches.add("Column '" + col + "' missing in target");
                    } else if (!targetSchema.get(col).equalsIgnoreCase(srcType)) {
                        mismatches.add("Column '" + col + "' type mismatch: source=" + srcType +
                                ", target=" + targetSchema.get(col));
                    }
                }
                for (String col : targetSchema.keySet()) {
                    if (!sourceSchema.containsKey(col)) {
                        mismatches.add("Column '" + col + "' present in target but not in source");
                    }
                }

                result.addDetail("Source columns: " + sourceSchema.size());
                result.addDetail("Target columns: " + targetSchema.size());
                if (mismatches.isEmpty()) {
                    result.pass("Schema matches between source and target (" + sourceSchema.size() + " columns)");
                } else {
                    result.fail(mismatches.size() + " schema discrepancy/discrepancies found");
                    mismatches.forEach(result::addDetail);
                }
            } catch (Exception e) {
                result.error("Schema check failed to execute: " + e.getMessage());
            }
            ctx.addResult(result);
        }
    }

    @Then("all schema checks should have passed")
    public void all_schema_checks_should_have_passed() {
        assertAllPassed(CheckResult.CheckType.SCHEMA);
    }

    // -----------------------------------------------------------------
    // 5. Sample Data Validation
    // -----------------------------------------------------------------

    @When("I run sample data validation for every configured table")
    public void i_run_sample_data_validation() {
        for (TableConfig cfg : ctx.getTableConfigs()) {
            CheckResult result = new CheckResult(cfg.getTableLabel(), CheckResult.CheckType.SAMPLE_DATA);
            try {
                List<Map<String, Object>> sourceSample = bq().getSampleRows(
                        cfg.getSourceTable(), List.of(), cfg.getKeyColumn(), cfg.getSampleSize());

                if (sourceSample.isEmpty()) {
                    result.pass("Source table returned no rows to sample");
                    ctx.addResult(result);
                    continue;
                }

                List<Object> keys = sourceSample.stream()
                        .map(row -> row.get(cfg.getKeyColumn()))
                        .filter(java.util.Objects::nonNull)
                        .toList();

                List<Map<String, Object>> targetRows = bq().getRowsByKeys(
                        cfg.getTargetTable(), cfg.getKeyColumn(), keys, cfg.getCompareColumns());

                Map<Object, Map<String, Object>> targetByKey = new LinkedHashMap<>();
                for (Map<String, Object> row : targetRows) {
                    targetByKey.put(row.get(cfg.getKeyColumn()), row);
                }

                List<String> mismatches = new ArrayList<>();
                List<String> compareCols = cfg.getCompareColumns();
                for (Map<String, Object> srcRow : sourceSample) {
                    Object key = srcRow.get(cfg.getKeyColumn());
                    Map<String, Object> tgtRow = targetByKey.get(key);
                    if (tgtRow == null) {
                        mismatches.add("Key '" + key + "' present in source sample but not found in target");
                        continue;
                    }
                    if (!compareCols.isEmpty()) {
                        for (String col : compareCols) {
                            Object sVal = srcRow.get(col);
                            Object tVal = tgtRow.get(col);
                            if (!java.util.Objects.equals(String.valueOf(sVal), String.valueOf(tVal))) {
                                mismatches.add("Key '" + key + "' column '" + col + "' differs: source=" + sVal +
                                        ", target=" + tVal);
                            }
                        }
                    }
                }

                // Attach the sampled source rows to the report regardless of pass/fail
                result.setSampleRows(sourceSample);

                if (mismatches.isEmpty()) {
                    result.pass("Sampled " + sourceSample.size() + " row(s) by '" + cfg.getKeyColumn() +
                            "' -- all matched in target" +
                            (compareCols.isEmpty() ? " (existence check only, no compare_columns configured)" : ""));
                } else {
                    result.fail(mismatches.size() + " mismatch(es) found in sampled rows");
                    mismatches.forEach(result::addDetail);
                }
            } catch (Exception e) {
                result.error("Sample data validation failed to execute: " + e.getMessage());
            }
            ctx.addResult(result);
        }
    }

    @Then("all sample data checks should have passed")
    public void all_sample_data_checks_should_have_passed() {
        assertAllPassed(CheckResult.CheckType.SAMPLE_DATA);
    }

    // -----------------------------------------------------------------
    // 6. Incremental Check
    // -----------------------------------------------------------------

    @When("I run incremental checks for every configured table")
    public void i_run_incremental_checks() {
        for (TableConfig cfg : ctx.getTableConfigs()) {
            CheckResult result = new CheckResult(cfg.getTableLabel(), CheckResult.CheckType.INCREMENTAL);
            try {
                java.time.LocalDate baseDate = java.time.LocalDate.parse(cfg.getTestDate());
                boolean anyMismatch = false;
                boolean anyTargetEmpty = false;

                // Check the last 5 days ending at test_date, using date_column
                // (e.g. LAST_MODIFIED_DATE) -- NOT key_column.
                for (int i = 0; i < 5; i++) {
                    java.time.LocalDate day = baseDate.minusDays(i);
                    String dayStr = day.toString();

                    long sourceCount = bq().getRowCount(cfg.getSourceTable(), cfg.getDateColumn(), dayStr);
                    long targetCount = bq().getRowCount(cfg.getTargetTable(), cfg.getDateColumn(), dayStr);

                    boolean dayMatches = sourceCount == targetCount;
                    if (!dayMatches) anyMismatch = true;
                    if (targetCount == 0 && sourceCount > 0) anyTargetEmpty = true;

                    result.addDetail(dayStr + " -- source: " + sourceCount + ", target: " + targetCount +
                            (dayMatches ? "  [MATCH]" : "  [MISMATCH]"));
                }

                if (!anyMismatch) {
                    result.pass("Incremental load verified across the last 5 day(s) ending " + cfg.getTestDate() +
                            " on column '" + cfg.getDateColumn() + "'");
                } else if (anyTargetEmpty) {
                    result.fail("Incremental data missing in target for one or more of the last 5 days ending " +
                            cfg.getTestDate());
                } else {
                    result.fail("Incremental load mismatch found in one or more of the last 5 days ending " +
                            cfg.getTestDate());
                }
            } catch (Exception e) {
                result.error("Incremental check failed to execute: " + e.getMessage());
            }
            ctx.addResult(result);
        }
    }

    @Then("all incremental checks should have passed")
    public void all_incremental_checks_should_have_passed() {
        assertAllPassed(CheckResult.CheckType.INCREMENTAL);
    }

    // -----------------------------------------------------------------
    // Shared assertion helper
    // -----------------------------------------------------------------

    private void assertAllPassed(CheckResult.CheckType type) {
        List<CheckResult> results = ctx.getAllResults().stream()
                .filter(r -> r.getCheckType() == type)
                .toList();
        List<CheckResult> failed = results.stream().filter(r -> !r.isPass()).toList();
        if (!failed.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(failed.size()).append(" of ").append(results.size())
                    .append(" ").append(type.label).append(" check(s) did not pass:\n");
            for (CheckResult r : failed) {
                sb.append("  - [").append(r.getStatus()).append("] ")
                        .append(r.getTableLabel()).append(": ").append(r.getMessage()).append("\n");
            }
            throw new AssertionError(sb.toString());
        }
    }
}
