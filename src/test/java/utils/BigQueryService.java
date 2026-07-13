package utils;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class BigQueryService {

    private static volatile BigQueryService instance;
    private final BigQuery bigQuery;

    private BigQueryService(String projectId) {
        try {
            BigQueryOptions.Builder builder = BigQueryOptions.newBuilder();
            if (projectId != null && !projectId.isBlank()) {
                builder.setProjectId(projectId);
            }
            this.bigQuery = builder.build().getService();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not initialize the BigQuery client" +
                    (projectId != null ? " (tried project '" + projectId + "')" : "") + ". " +
                    "Make sure you're authenticated (run: gcloud auth application-default login) " +
                    "and a billing project is set -- either via 'gcloud config set project <id>', " +
                    "the GOOGLE_CLOUD_PROJECT environment variable, or -Dbq.project=<id> on the mvn command line. " +
                    "Original error: " + e.getMessage(), e);
        }
    }

    /** Returns the shared client, creating it on first call using the resolved project ID. */
    public static BigQueryService getInstance(String fallbackProjectId) {
        if (instance == null) {
            synchronized (BigQueryService.class) {
                if (instance == null) {
                    instance = new BigQueryService(resolveProjectId(fallbackProjectId));
                }
            }
        }
        return instance;
    }

    private static String resolveProjectId(String fallbackProjectId) {
        String projectId = System.getProperty("bq.project");
        if (isBlank(projectId)) projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
        if (isBlank(projectId)) projectId = System.getenv("GCP_PROJECT");
        if (isBlank(projectId)) projectId = fallbackProjectId;
        return projectId;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private TableResult runQuery(String sql) {
        try {
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(sql).build();
            return bigQuery.query(queryConfig);
        } catch (Exception e) {
            throw new RuntimeException("BigQuery query failed: " + sql + " -- " + e.getMessage(), e);
        }
    }

    /** Row count for a table, optionally filtered to a single date on dateColumn. */
    public long getRowCount(String table, String dateColumn, String testDate) {
        String sql;
        if (dateColumn != null && !dateColumn.isEmpty() && testDate != null && !testDate.isEmpty()) {
            sql = String.format("SELECT COUNT(*) AS cnt FROM `%s` WHERE DATE(%s) = DATE('%s')",
                    table, dateColumn, testDate);
        } else {
            sql = String.format("SELECT COUNT(*) AS cnt FROM `%s`", table);
        }
        TableResult result = runQuery(sql);
        for (FieldValueList row : result.iterateAll()) {
            return row.get("cnt").getLongValue();
        }
        return 0L;
    }

    /** Returns up to `limit` duplicate key values (with their counts) found in keyColumn. */
    public List<Map<String, Object>> findDuplicates(String table, String keyColumn, int limit) {
        String sql = String.format(
                "SELECT %s AS dup_key, COUNT(*) AS dup_count FROM `%s` " +
                        "GROUP BY %s HAVING COUNT(*) > 1 ORDER BY dup_count DESC LIMIT %d",
                keyColumn, table, keyColumn, limit);
        TableResult result = runQuery(sql);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (FieldValueList row : result.iterateAll()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put(keyColumn, row.get("dup_key").getValue());
            map.put("occurrences", row.get("dup_count").getLongValue());
            rows.add(map);
        }
        return rows;
    }

    /** Null count per requested column. */
    public Map<String, Long> getNullCounts(String table, List<String> columns) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (columns.isEmpty()) {
            return result;
        }
        StringBuilder selects = new StringBuilder();
        for (String col : columns) {
            if (selects.length() > 0) selects.append(", ");
            selects.append(String.format(
                    "COUNTIF(%s IS NULL) AS null_%s", col, sanitizeAlias(col)));
        }
        String sql = String.format("SELECT %s FROM `%s`", selects, table);
        TableResult tr = runQuery(sql);
        for (FieldValueList row : tr.iterateAll()) {
            for (String col : columns) {
                result.put(col, row.get("null_" + sanitizeAlias(col)).getLongValue());
            }
        }
        return result;
    }

    /** Column name -> data type, from INFORMATION_SCHEMA.COLUMNS. */
    public Map<String, String> getSchema(String table) {
        String[] parts = table.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Expected project.dataset.table, got: " + table);
        }
        String project = parts[0];
        String dataset = parts[1];
        String tableName = parts[2];
        String sql = String.format(
                "SELECT column_name, data_type FROM `%s.%s.INFORMATION_SCHEMA.COLUMNS` " +
                        "WHERE table_name = '%s' ORDER BY ordinal_position",
                project, dataset, tableName);
        TableResult tr = runQuery(sql);
        Map<String, String> schema = new LinkedHashMap<>();
        for (FieldValueList row : tr.iterateAll()) {
            schema.put(row.get("column_name").getStringValue(), row.get("data_type").getStringValue());
        }
        return schema;
    }

    /** Sample of rows from a table (specific columns, or all columns if columns is empty). */
    public List<Map<String, Object>> getSampleRows(String table, List<String> columns, String orderByColumn, int limit) {
        String colList = columns.isEmpty() ? "*" : String.join(", ", columns);
        String orderClause = (orderByColumn != null && !orderByColumn.isEmpty())
                ? " ORDER BY " + orderByColumn : "";
        String sql = String.format("SELECT %s FROM `%s`%s LIMIT %d", colList, table, orderClause, limit);
        return toListOfMaps(runQuery(sql));
    }

    /** Fetch rows matching a specific set of key values -- used for source/target row diffing. */
    public List<Map<String, Object>> getRowsByKeys(String table, String keyColumn, List<Object> keyValues, List<String> columns) {
        if (keyValues.isEmpty()) {
            return new ArrayList<>();
        }
        String colList = columns.isEmpty() ? "*" : String.join(", ", columns);
        StringBuilder inClause = new StringBuilder();
        for (Object v : keyValues) {
            if (inClause.length() > 0) inClause.append(", ");
            inClause.append("'").append(String.valueOf(v).replace("'", "\\'")).append("'");
        }
        String sql = String.format("SELECT %s, %s FROM `%s` WHERE %s IN (%s)",
                keyColumn, colList.equals("*") ? "* EXCEPT(" + keyColumn + ")" : colList,
                table, keyColumn, inClause);
        return toListOfMaps(runQuery(sql));
    }

    private List<Map<String, Object>> toListOfMaps(TableResult result) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Schema schema = result.getSchema();
        for (FieldValueList row : result.iterateAll()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < schema.getFields().size(); i++) {
                String fieldName = schema.getFields().get(i).getName();
                try {
                    map.put(fieldName, row.get(i).getValue());
                } catch (Exception e) {
                    map.put(fieldName, null);
                }
            }
            rows.add(map);
        }
        return rows;
    }

    /**
     * Full-table row hash reconciliation: hashes every row on both sides (using the
     * given columns, or every non-key column from source's schema if columns is empty),
     * then returns only the rows whose hash differs -- i.e. actually mismatched, missing
     * on one side, or extra on one side. Cheap at scale because only the small set of
     * mismatched keys needs any further column-level drill-down (done separately by the
     * caller via getRowsByKeys).
     *
     * ASSUMPTION: source and target share the same column names for the compared columns.
     * ASSUMPTION: compared columns are scalar (CAST-able to STRING) -- not ARRAY/STRUCT.
     */
    public List<Map<String, Object>> findRowHashMismatches(String sourceTable, String targetTable,
                                                             String keyColumn, List<String> compareColumns,
                                                             int limit) {
        List<String> cols = compareColumns;
        if (cols.isEmpty()) {
            Map<String, String> schema = getSchema(sourceTable);
            cols = new ArrayList<>();
            for (String col : schema.keySet()) {
                if (!col.equalsIgnoreCase(keyColumn)) {
                    cols.add(col);
                }
            }
        }

        StringBuilder structCols = new StringBuilder();
        for (String c : cols) {
            if (structCols.length() > 0) structCols.append(", ");
            structCols.append("CAST(").append(c).append(" AS STRING) AS ").append(c);
        }

        String sql = String.format(
                "WITH src AS (" +
                        "  SELECT %s AS row_key, TO_HEX(SHA256(TO_JSON_STRING(STRUCT(%s)))) AS row_hash FROM `%s`" +
                        "), tgt AS (" +
                        "  SELECT %s AS row_key, TO_HEX(SHA256(TO_JSON_STRING(STRUCT(%s)))) AS row_hash FROM `%s`" +
                        ") " +
                        "SELECT COALESCE(src.row_key, tgt.row_key) AS row_key, " +
                        "  CASE WHEN src.row_key IS NULL THEN 'MISSING_IN_SOURCE' " +
                        "       WHEN tgt.row_key IS NULL THEN 'MISSING_IN_TARGET' " +
                        "       ELSE 'HASH_MISMATCH' END AS mismatch_type " +
                        "FROM src FULL OUTER JOIN tgt ON src.row_key = tgt.row_key " +
                        "WHERE src.row_hash IS DISTINCT FROM tgt.row_hash " +
                        "LIMIT %d",
                keyColumn, structCols, sourceTable, keyColumn, structCols, targetTable, limit);

        return toListOfMaps(runQuery(sql));
    }

    private String sanitizeAlias(String col) {
        return col.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
