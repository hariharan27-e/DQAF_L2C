package model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CheckResult {

    public enum Status { PASS, FAIL, ERROR }

    public enum CheckType {
        ROW_COUNT("Row Count"),
        DUPLICATE("Duplicate Check"),
        NULL_CHECK("Null Check"),
        SCHEMA("Schema Check"),
        SAMPLE_DATA("Sample Data Validation"),
        INCREMENTAL("Incremental Check");

        public final String label;
        CheckType(String label) { this.label = label; }
    }

    private final String tableLabel;
    private final CheckType checkType;
    private Status status;
    private String message;
    private final List<String> details = new ArrayList<>();
    private List<Map<String, Object>> sampleRows = new ArrayList<>();

    public CheckResult(String tableLabel, CheckType checkType) {
        this.tableLabel = tableLabel;
        this.checkType = checkType;
        this.status = Status.PASS;
        this.message = "";
    }

    public void pass(String message) {
        this.status = Status.PASS;
        this.message = message;
    }

    public void fail(String message) {
        this.status = Status.FAIL;
        this.message = message;
    }

    public void error(String message) {
        this.status = Status.ERROR;
        this.message = message;
    }

    public void addDetail(String detail) {
        this.details.add(detail);
    }

    public void setSampleRows(List<Map<String, Object>> rows) {
        this.sampleRows = rows;
    }

    public String getTableLabel() { return tableLabel; }
    public CheckType getCheckType() { return checkType; }
    public Status getStatus() { return status; }
    public String getMessage() { return message; }
    public List<String> getDetails() { return details; }
    public List<Map<String, Object>> getSampleRows() { return sampleRows; }

    public boolean isPass() { return status == Status.PASS; }
}
