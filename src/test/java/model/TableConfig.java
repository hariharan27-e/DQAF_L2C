package model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents one row of tables_config.csv.
 * Multi-value fields (null_columns, compare_columns) are pipe "|" separated,
 * e.g. "app_id|developer_id" -- comma is reserved as the CSV delimiter.
 */
public class TableConfig {

    private final String sourceTable;
    private final String targetTable;
    private final String keyColumn;
    private final String dateColumn;
    private final String testDate;
    private final List<String> nullColumns;
    private final int sampleSize;
    private final List<String> compareColumns;

    public TableConfig(String sourceTable, String targetTable, String keyColumn, String dateColumn,
                        String testDate, String nullColumnsRaw, String sampleSizeRaw, String compareColumnsRaw) {
        this.sourceTable = trim(sourceTable);
        this.targetTable = trim(targetTable);
        this.keyColumn = trim(keyColumn);
        this.dateColumn = trim(dateColumn);
        this.testDate = trim(testDate);
        this.nullColumns = splitMulti(nullColumnsRaw);
        this.sampleSize = parseSampleSize(sampleSizeRaw);
        this.compareColumns = splitMulti(compareColumnsRaw);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static List<String> splitMulti(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return result;
        }
        for (String part : raw.split("\\|")) {
            String p = part.trim();
            if (!p.isEmpty()) {
                result.add(p);
            }
        }
        return result;
    }

    private static int parseSampleSize(String raw) {
        try {
            return Integer.parseInt(trim(raw));
        } catch (Exception e) {
            return 10;
        }
    }

    public String getSourceTable() { return sourceTable; }
    public String getTargetTable() { return targetTable; }
    public String getKeyColumn() { return keyColumn; }
    public String getDateColumn() { return dateColumn; }
    public String getTestDate() { return testDate; }
    public List<String> getNullColumns() { return nullColumns; }
    public int getSampleSize() { return sampleSize; }
    public List<String> getCompareColumns() { return compareColumns; }

    /** Short label used to group results/report sections per table. */
    public String getTableLabel() {
        String[] srcParts = sourceTable.split("\\.");
        String[] tgtParts = targetTable.split("\\.");
        String srcShort = srcParts.length > 0 ? srcParts[srcParts.length - 1] : sourceTable;
        String tgtShort = tgtParts.length > 0 ? tgtParts[tgtParts.length - 1] : targetTable;
        return srcShort + "  →  " + tgtShort;
    }

    @Override
    public String toString() {
        return "TableConfig{" + sourceTable + " -> " + targetTable + "}";
    }
}
