package utils;

import model.TableConfig;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ConfigLoader {

    /**
     * Loads table configs from a CSV file. First looks on the classpath
     * (src/test/resources/&lt;fileName&gt;), then falls back to a plain
     * filesystem path, so the same step works whether the file was packaged
     * into resources or just dropped next to the project.
     */
    public static List<TableConfig> load(String fileName) throws IOException {
        List<TableConfig> configs = new ArrayList<>();

        Reader reader = openReader(fileName);
        try (CSVParser parser = new CSVParser(reader,
                CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setTrim(true)
                        .setIgnoreSurroundingSpaces(true)
                        .build())) {

            for (CSVRecord record : parser) {
                configs.add(new TableConfig(
                        record.get("source_table"),
                        record.get("target_table"),
                        record.get("key_column"),
                        record.get("date_column"),
                        record.get("test_date"),
                        record.isMapped("null_columns") ? record.get("null_columns") : "",
                        record.isMapped("sample_size") ? record.get("sample_size") : "10",
                        record.isMapped("compare_columns") ? record.get("compare_columns") : ""
                ));
            }
        }

        if (configs.isEmpty()) {
            throw new IllegalStateException("No table configurations found in " + fileName);
        }
        return configs;
    }

    private static Reader openReader(String fileName) throws IOException {
        InputStream classpathStream = ConfigLoader.class.getClassLoader().getResourceAsStream(fileName);
        if (classpathStream != null) {
            return new InputStreamReader(classpathStream, StandardCharsets.UTF_8);
        }
        return new FileReader(fileName);
    }
}
