package context;

import model.CheckResult;
import model.TableConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple singleton shared across all Cucumber steps in a single JVM run.
 * Holds the loaded table configs and every check result produced,
 * so the report generator (run once in an @AfterAll hook) can build
 * one consolidated, per-table HTML report at the end of the whole suite.
 */
public class TestContext {

    private static final TestContext INSTANCE = new TestContext();

    private List<TableConfig> tableConfigs = new ArrayList<>();
    private final List<CheckResult> allResults = new ArrayList<>();

    private TestContext() { }

    public static TestContext getInstance() {
        return INSTANCE;
    }

    public void setTableConfigs(List<TableConfig> configs) {
        this.tableConfigs = configs;
    }

    public List<TableConfig> getTableConfigs() {
        return tableConfigs;
    }

    public void addResult(CheckResult result) {
        allResults.add(result);
    }

    public List<CheckResult> getAllResults() {
        return allResults;
    }
}
