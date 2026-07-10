package report;

import model.CheckResult;
import model.TableConfig;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates one standalone HTML report per table (each downloadable/shareable on
 * its own), plus an index.html that links to all of them and shows the overall
 * pass/fail/error totals across every table.
 */
public class ReportGenerator {

    private static final String OUTPUT_DIR = "target/dqaf-report";

    public static void generate(List<TableConfig> tableConfigs, List<CheckResult> allResults) {
        try {
            Files.createDirectories(Paths.get(OUTPUT_DIR));

            List<TableSummary> summaries = new ArrayList<>();

            for (TableConfig config : tableConfigs) {
                String label = config.getTableLabel();
                List<CheckResult> tableResults = allResults.stream()
                        .filter(r -> r.getTableLabel().equals(label))
                        .toList();

                String fileName = sanitizeFileName(label) + ".html";
                String html = buildTableReportPage(config, tableResults);

                try (FileWriter writer = new FileWriter(OUTPUT_DIR + "/" + fileName)) {
                    writer.write(html);
                }

                summaries.add(new TableSummary(config, tableResults, fileName));
            }

            String indexHtml = buildIndexPage(summaries, allResults);
            try (FileWriter writer = new FileWriter(OUTPUT_DIR + "/index.html")) {
                writer.write(indexHtml);
            }

            System.out.println("\n===================================================");
            System.out.println(" DQAF Reports generated in: " + OUTPUT_DIR);
            System.out.println(" Open " + OUTPUT_DIR + "/index.html to see all tables,");
            System.out.println(" or open each table's own report file directly.");
            System.out.println("===================================================\n");
        } catch (IOException e) {
            System.err.println("Failed to generate DQAF HTML report: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // Index page -- links to every per-table report + overall totals
    // -----------------------------------------------------------------

    private static String buildIndexPage(List<TableSummary> summaries, List<CheckResult> allResults) {
        StringBuilder sb = new StringBuilder();
        sb.append(htmlHeader("DQAF Validation Suite - Overview"));
        sb.append(buildSummaryBanner("DQAF Validation Suite &mdash; Equinix L2C", allResults));

        sb.append("<div class='index-grid'>");
        for (TableSummary summary : summaries) {
            boolean allPassed = summary.passed == summary.total;
            sb.append("<a class='index-card ").append(allPassed ? "card-pass" : "card-fail")
                    .append("' href='").append(summary.fileName).append("'>");
            sb.append("<div class='index-card-title'>").append(escape(summary.config.getTableLabel())).append("</div>");
            sb.append("<div class='index-card-badge ").append(allPassed ? "badge-pass" : "badge-fail").append("'>")
                    .append(allPassed ? "ALL CHECKS PASSED" : "ATTENTION NEEDED").append("</div>");
            sb.append("<div class='index-card-stats'>");
            sb.append("<span class='mini-stat pass'>").append(summary.passed).append(" pass</span>");
            sb.append("<span class='mini-stat fail'>").append(summary.failed).append(" fail</span>");
            sb.append("<span class='mini-stat error'>").append(summary.errored).append(" error</span>");
            sb.append("</div>");
            sb.append("<div class='index-card-link'>Open full report &rarr;</div>");
            sb.append("</a>");
        }
        sb.append("</div>");

        sb.append(htmlFooter());
        return sb.toString();
    }

    // -----------------------------------------------------------------
    // Per-table report page (standalone, downloadable on its own)
    // -----------------------------------------------------------------

    private static String buildTableReportPage(TableConfig config, List<CheckResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append(htmlHeader("DQAF Report - " + config.getTableLabel()));
        sb.append("<p class='back-link'><a href='index.html'>&larr; Back to all tables</a></p>");
        sb.append(buildSummaryBanner(escape(config.getTableLabel()), results));
        sb.append(buildTableMeta(config));
        sb.append(buildChecksTable(results));

        for (CheckResult r : results) {
            if (r.getSampleRows() != null && !r.getSampleRows().isEmpty()) {
                sb.append(buildSampleDataBlock(r));
            }
        }

        sb.append(htmlFooter());
        return sb.toString();
    }

    private static String buildTableMeta(TableConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='table-meta'>");
        sb.append("<span><b>Source:</b> ").append(escape(config.getSourceTable())).append("</span>");
        sb.append("<span><b>Target:</b> ").append(escape(config.getTargetTable())).append("</span>");
        sb.append("<span><b>Key Column:</b> ").append(escape(config.getKeyColumn())).append("</span>");
        sb.append("<span><b>Incremental Column:</b> ").append(escape(config.getDateColumn())).append("</span>");
        sb.append("<span><b>Test Date:</b> ").append(escape(config.getTestDate())).append("</span>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildChecksTable(List<CheckResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table class='checks-table'>");
        sb.append("<tr><th>Check</th><th>Status</th><th>Details</th></tr>");
        for (CheckResult r : results) {
            String statusClass = switch (r.getStatus()) {
                case PASS -> "status-pass";
                case FAIL -> "status-fail";
                case ERROR -> "status-error";
            };
            sb.append("<tr>");
            sb.append("<td>").append(r.getCheckType().label).append("</td>");
            sb.append("<td><span class='status-pill ").append(statusClass).append("'>")
                    .append(r.getStatus()).append("</span></td>");
            sb.append("<td>");
            sb.append(escape(r.getMessage()));
            if (!r.getDetails().isEmpty()) {
                sb.append("<ul class='detail-list'>");
                for (String d : r.getDetails()) {
                    sb.append("<li>").append(escape(d)).append("</li>");
                }
                sb.append("</ul>");
            }
            sb.append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    // -----------------------------------------------------------------
    // Shared: summary banner with Total / Pass / Fail / Error counts
    // -----------------------------------------------------------------

    private static String buildSummaryBanner(String title, List<CheckResult> results) {
        long total = results.size();
        long passed = results.stream().filter(CheckResult::isPass).count();
        long failed = results.stream().filter(r -> r.getStatus() == CheckResult.Status.FAIL).count();
        long errored = results.stream().filter(r -> r.getStatus() == CheckResult.Status.ERROR).count();
        String generatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss"));

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='summary-banner'>");
        sb.append("<h1>").append(title).append("</h1>");
        sb.append("<p class='timestamp'>Generated: ").append(generatedAt).append("</p>");
        sb.append("<div class='stat-cards'>");
        sb.append(statCard("Total Test Cases", total, "neutral"));
        sb.append(statCard("Passed", passed, "pass"));
        sb.append(statCard("Failed", failed, "fail"));
        sb.append(statCard("Errors", errored, "error"));
        sb.append("</div></div>");
        return sb.toString();
    }

    private static String statCard(String label, long value, String cssClass) {
        return "<div class='stat-card " + cssClass + "'><div class='stat-value'>" + value +
                "</div><div class='stat-label'>" + label + "</div></div>";
    }

    // -----------------------------------------------------------------
    // Sample data block
    // -----------------------------------------------------------------

    private static String buildSampleDataBlock(CheckResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='sample-data-block'>");
        sb.append("<h3>Sample Data &mdash; ").append(r.getCheckType().label).append("</h3>");

        List<Map<String, Object>> rows = r.getSampleRows();
        if (rows.isEmpty()) {
            sb.append("<p class='no-data'>No sample rows captured.</p></div>");
            return sb.toString();
        }

        Map<String, Boolean> columns = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            for (String k : row.keySet()) {
                columns.putIfAbsent(k, true);
            }
        }

        sb.append("<div class='sample-table-wrap'><table class='sample-table'>");
        sb.append("<tr>");
        for (String col : columns.keySet()) {
            sb.append("<th>").append(escape(col)).append("</th>");
        }
        sb.append("</tr>");
        for (Map<String, Object> row : rows) {
            sb.append("<tr>");
            for (String col : columns.keySet()) {
                Object val = row.get(col);
                sb.append("<td>").append(val == null ? "<i>null</i>" : escape(String.valueOf(val))).append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</table></div></div>");
        return sb.toString();
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static String sanitizeFileName(String label) {
        return label.replaceAll("[^a-zA-Z0-9_.-]+", "_").replaceAll("_+", "_");
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String htmlHeader(String title) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
                "<title>" + escape(title) + "</title>" +
                "<style>" + css() + "</style></head><body><div class='container'>";
    }

    private static String htmlFooter() {
        return "</div></body></html>";
    }

    private static class TableSummary {
        final TableConfig config;
        final String fileName;
        final long total, passed, failed, errored;

        TableSummary(TableConfig config, List<CheckResult> results, String fileName) {
            this.config = config;
            this.fileName = fileName;
            this.total = results.size();
            this.passed = results.stream().filter(CheckResult::isPass).count();
            this.failed = results.stream().filter(r -> r.getStatus() == CheckResult.Status.FAIL).count();
            this.errored = results.stream().filter(r -> r.getStatus() == CheckResult.Status.ERROR).count();
        }
    }

    private static String css() {
        return """
            :root {
              --pass: #1e9e5a; --fail: #d64545; --error: #e08a1e; --neutral: #4a5568;
              --bg: #f4f6f8; --card-bg: #ffffff; --border: #e2e8f0; --text: #1a202c;
            }
            * { box-sizing: border-box; }
            body { font-family: 'Segoe UI', Roboto, Arial, sans-serif; background: var(--bg); color: var(--text); margin:0; padding:0; }
            .container { max-width: 1200px; margin: 0 auto; padding: 24px; }
            .back-link { margin: 0 0 12px 0; }
            .back-link a { color:#3455db; text-decoration:none; font-size:14px; font-weight:600; }
            .back-link a:hover { text-decoration:underline; }
            .summary-banner { background: linear-gradient(135deg,#1a2a4a,#243b6b); color:#fff; border-radius:10px; padding:28px 32px; margin-bottom:28px; }
            .summary-banner h1 { margin:0 0 4px 0; font-size:24px; }
            .timestamp { opacity:0.8; margin:0 0 18px 0; font-size:13px; }
            .stat-cards { display:flex; gap:16px; flex-wrap:wrap; }
            .stat-card { background: rgba(255,255,255,0.08); border-radius:8px; padding:14px 22px; min-width:120px; text-align:center; border:1px solid rgba(255,255,255,0.15);}
            .stat-card .stat-value { font-size:28px; font-weight:700; }
            .stat-card .stat-label { font-size:12px; opacity:0.85; text-transform:uppercase; letter-spacing:0.5px; }
            .stat-card.pass .stat-value { color:#4ade80; }
            .stat-card.fail .stat-value { color:#f87171; }
            .stat-card.error .stat-value { color:#fbbf24; }
            .index-grid { display:grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap:18px; }
            .index-card { display:block; background: var(--card-bg); border-radius:10px; padding:20px; text-decoration:none; color: var(--text); border:1px solid var(--border); box-shadow:0 1px 3px rgba(0,0,0,0.06); transition: transform 0.1s ease; }
            .index-card:hover { transform: translateY(-2px); box-shadow:0 4px 10px rgba(0,0,0,0.08); }
            .card-pass { border-left:5px solid var(--pass); }
            .card-fail { border-left:5px solid var(--fail); }
            .index-card-title { font-size:15px; font-weight:700; margin-bottom:8px; }
            .index-card-badge { display:inline-block; padding:4px 10px; border-radius:14px; font-size:11px; font-weight:700; margin-bottom:12px; }
            .index-card-stats { display:flex; gap:10px; margin-bottom:10px; font-size:12px; }
            .mini-stat.pass { color: var(--pass); font-weight:600; }
            .mini-stat.fail { color: var(--fail); font-weight:600; }
            .mini-stat.error { color: var(--error); font-weight:600; }
            .index-card-link { font-size:13px; color:#3455db; font-weight:600; }
            .table-meta { display:flex; gap:24px; flex-wrap:wrap; padding:14px 22px; font-size:13px; color:#4a5568; background:#fafbfc; border-radius:8px 8px 0 0; border:1px solid var(--border); border-bottom:none; }
            .checks-table { width:100%; border-collapse:collapse; background:var(--card-bg); border:1px solid var(--border); border-radius:0 0 8px 8px; margin-bottom:24px; overflow:hidden; }
            .checks-table th { text-align:left; padding:10px 22px; font-size:12px; text-transform:uppercase; letter-spacing:0.5px; color:#718096; background:#fafbfc; border-bottom:1px solid var(--border); }
            .checks-table td { padding:12px 22px; border-bottom:1px solid var(--border); font-size:14px; vertical-align:top; }
            .status-pill { padding:4px 12px; border-radius:14px; font-size:11px; font-weight:700; letter-spacing:0.5px; }
            .status-pass { background:#e8f8ef; color: var(--pass); }
            .status-fail { background:#fdecec; color: var(--fail); }
            .status-error { background:#fff6e5; color: var(--error); }
            .detail-list { margin:6px 0 0 0; padding-left:18px; color:#4a5568; font-size:13px; }
            .sample-data-block { padding:18px 22px; background:#fcfcfd; border:1px solid var(--border); border-radius:8px; margin-bottom:20px; }
            .sample-data-block h3 { margin:0 0 12px 0; font-size:14px; color:#2d3748; }
            .sample-table-wrap { overflow-x:auto; }
            .sample-table { border-collapse:collapse; width:100%; font-size:12.5px; }
            .sample-table th { background:#eef2f7; padding:8px 12px; text-align:left; border:1px solid var(--border); white-space:nowrap; }
            .sample-table td { padding:8px 12px; border:1px solid var(--border); white-space:nowrap; }
            .sample-table tr:nth-child(even) { background:#fafbfc; }
            .no-data { color:#a0aec0; font-style:italic; font-size:13px; }
            """;
    }
}
