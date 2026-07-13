Feature: Data Quality Validation Suite - Config Driven
  As a Data Quality Engineer
  I want to validate all tables listed in a config file
  So that adding a new table just means adding a row to a CSV, not editing code

  Background:
    Given I load table configurations from "tables_config.csv"

  Scenario: Row Count Reconciliation for every configured table
    When I run row count checks for every configured table
    Then all row count checks should have passed

  Scenario: Duplicate Key Check for every configured table
    When I run duplicate checks for every configured table
    Then all duplicate checks should have passed

  Scenario: Null Value Check for every configured table
    When I run null checks for every configured table
    Then all null checks should have passed

  Scenario: Schema Check for every configured table
    When I run schema checks for every configured table
    Then all schema checks should have passed

  Scenario: Sample Data Validation for every configured table
    When I run sample data validation for every configured table
    Then all sample data checks should have passed

  Scenario: Incremental Load Check for every configured table
    When I run incremental checks for every configured table
    Then all incremental checks should have passed

  Scenario: Row Hash Reconciliation for every configured table
    When I run row hash reconciliation for every configured table
    Then all row hash reconciliation checks should have passed
