/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.upgrade;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.test.upgrade.client.OpenSearchSchemaTestClient;
import java.io.IOException;
import java.util.Map;
import org.opensearch.client.opensearch.indices.IndexState;
import org.opensearch.client.opensearch.indices.TemplateMapping;
import org.opensearch.client.opensearch.indices.get_alias.IndexAliases;
import org.opensearch.client.opensearch.indices.get_mapping.IndexMappingRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpgradeOpenSearchSchemaIT
    extends AbstractDatabaseSchemaIT<OpenSearchSchemaTestClient> {
  private static final Logger log = LoggerFactory.getLogger(UpgradeOpenSearchSchemaIT.class);

  Map<String, IndexState> expectedSettings;
  Map<String, IndexMappingRecord> expectedMappings;
  Map<String, IndexAliases> expectedAliases;
  Map<String, TemplateMapping> expectedTemplates;

  @Override
  protected String getOptimizeUpdateLogPath() {
    return getBuildDirectory() + "/os-update-schema-optimize-upgrade.log";
  }

  @Override
  protected String getNewOptimizeOutputLogPath() {
    return getBuildDirectory() + "/os-update-schema-new-optimize-startup.log";
  }

  @Override
  protected String getOldOptimizeOutputLogPath() {
    return getBuildDirectory() + "/os-update-schema-old-optimize-startup.log";
  }

  @Override
  protected void assertMigratedDatabaseIndicesMatchExpected() throws IOException {
    // Indices
    log.info(
        "Expected settings size: {}, keys: {}", expectedSettings.size(), expectedSettings.keySet());
    final Map<String, IndexState> newSettings = newDatabaseSchemaClient.getSettings();
    log.info("Actual settings size: {}, keys: {}", newSettings.size(), newSettings.keySet());
    assertThat(newSettings).isEqualTo(expectedSettings);
    assertThat(newDatabaseSchemaClient.getMappings()).isEqualTo(expectedMappings);
  }

  @Override
  protected void assertMigratedDatabaseAliasesMatchExpected() throws IOException {
    log.info(
        "Expected aliases size: {}, keys: {}", expectedAliases.size(), expectedAliases.keySet());
    final Map<String, IndexAliases> newAliases = newDatabaseSchemaClient.getAliases();
    log.info("Actual aliases size: {}, keys: {}", newAliases.size(), newAliases.keySet());
    assertThat(newAliases).isEqualTo(expectedAliases);
  }

  @Override
  protected void assertMigratedDatabaseTemplatesMatchExpected() throws IOException {
    // Templates
    log.info(
        "Expected templates size: {}, names: {}",
        expectedTemplates.size(),
        expectedTemplates.keySet());
    final Map<String, TemplateMapping> newTemplates = newDatabaseSchemaClient.getTemplates();
    log.info("Actual templates size: {}, names: {}", newTemplates.size(), newTemplates.keySet());
    assertThat(newTemplates).isEqualTo(expectedTemplates);
  }

  @Override
  protected void saveNewOptimizeDatabaseStatus() throws IOException {
    expectedSettings = newDatabaseSchemaClient.getSettings();
    expectedMappings = newDatabaseSchemaClient.getMappings();
    expectedAliases = newDatabaseSchemaClient.getAliases();
    expectedTemplates = newDatabaseSchemaClient.getTemplates();
  }

  @Override
  protected void initializeClientAndCleanDatabase() throws IOException {
    oldDatabaseSchemaClient = new OpenSearchSchemaTestClient("old", getOldDatabasePort());
    oldDatabaseSchemaClient.cleanIndicesAndTemplates();
    newDatabaseSchemaClient = new OpenSearchSchemaTestClient("new", getNewDatabasePort());
    newDatabaseSchemaClient.cleanIndicesAndTemplates();
  }

  @Override
  protected DatabaseType getDatabaseType() {
    return DatabaseType.OPENSEARCH;
  }
}
