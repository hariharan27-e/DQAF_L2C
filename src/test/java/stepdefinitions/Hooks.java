package stepdefinitions;

import context.TestContext;
import io.cucumber.java.AfterAll;
import report.ReportGenerator;

public class Hooks {

    @AfterAll
    public static void generateFinalReport() {
        TestContext ctx = TestContext.getInstance();
        if (ctx.getTableConfigs().isEmpty()) {
            return; // nothing ran
        }
        ReportGenerator.generate(ctx.getTableConfigs(), ctx.getAllResults());
    }
}
