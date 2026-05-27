package com.classroom.bdd.config;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * JUnit Platform Suite entry-point that runs all Cucumber BDD scenarios.
 *
 * <p>Run via Maven:
 * <pre>
 *   # All scenarios
 *   mvn test -pl integration-tests
 *
 *   # Smoke tests only
 *   mvn test -pl integration-tests -Dcucumber.filter.tags="@smoke"
 *
 *   # End-to-end tests
 *   mvn test -pl integration-tests -Dcucumber.filter.tags="@e2e"
 * </pre>
 *
 * <p>Or right-click this class in IntelliJ and select "Run".
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(
        key   = GLUE_PROPERTY_NAME,
        value = "com.classroom.bdd")
@ConfigurationParameter(
        key   = PLUGIN_PROPERTY_NAME,
        value = "pretty,"
              + "html:target/cucumber-reports/cucumber.html,"
              + "json:target/cucumber-reports/cucumber.json,"
              + "junit:target/cucumber-reports/cucumber.xml")
public class CucumberSuite {
    // Intentionally empty
}

