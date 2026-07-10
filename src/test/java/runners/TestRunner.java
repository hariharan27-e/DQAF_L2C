package runners;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(
        features = "src/test/resources/features",
        glue = {"stepdefinitions"},
        plugin = {
                "pretty",
                "summary",
                "html:target/cucumber-reports/report.html",
                "json:target/cucumber-reports/report.json"
        },
        monochrome = true
)
public class TestRunner {
    // No code needed here -- this class just wires everything together.
    // The custom colored per-table report (target/dqaf-report/index.html)
    // is generated separately by stepdefinitions.Hooks after all scenarios run.
}
