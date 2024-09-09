/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.upgrade;

import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.test.upgrade.client.AbstractDatabaseSchemaTestClient;
import io.camunda.optimize.test.upgrade.wrapper.OptimizeWrapper;
import java.io.FileWriter;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractDatabaseSchemaIT<T extends AbstractDatabaseSchemaTestClient> {
  private static final Logger log = LoggerFactory.getLogger(AbstractDatabaseSchemaIT.class);
  protected T oldDatabaseSchemaClient;
  protected T newDatabaseSchemaClient;
  private final String previousVersion = System.getProperties().getProperty("previousVersion");
  private final String currentVersion = System.getProperties().getProperty("currentVersion");
  private final String buildDirectory = System.getProperties().getProperty("buildDirectory");
  private final Integer oldDatabasePort =
      Integer.valueOf(System.getProperties().getProperty("oldDatabasePort"));
  private final Integer newDatabasePort =
      Integer.valueOf(System.getProperties().getProperty("newDatabasePort"));
  private FileWriter oldOptimizeOutputWriter;
  private FileWriter newOptimizeOutputWriter;
  private FileWriter upgradeOutputWriter;

  protected abstract String getOptimizeUpdateLogPath();

  protected abstract String getNewOptimizeOutputLogPath();

  protected abstract String getOldOptimizeOutputLogPath();

  protected abstract void assertMigratedDatabaseIndicesMatchExpected() throws IOException;

  protected abstract void assertMigratedDatabaseAliasesMatchExpected() throws IOException;

  protected abstract void assertMigratedDatabaseTemplatesMatchExpected() throws IOException;

  protected abstract void saveNewOptimizeDatabaseStatus() throws IOException;

  protected abstract void initializeClientAndCleanDatabase() throws IOException;

  protected abstract DatabaseType getDatabaseType();

  @BeforeEach
  public void logFilePrepare() throws IOException {
    oldOptimizeOutputWriter = new FileWriter(getOldOptimizeOutputLogPath());
    newOptimizeOutputWriter = new FileWriter(getNewOptimizeOutputLogPath());
    upgradeOutputWriter = new FileWriter(getOptimizeUpdateLogPath());
  }

  @AfterEach
  public void logFileFlush() throws IOException {
    oldOptimizeOutputWriter.flush();
    oldOptimizeOutputWriter.close();
    newOptimizeOutputWriter.flush();
    newOptimizeOutputWriter.close();
    upgradeOutputWriter.flush();
    upgradeOutputWriter.close();
  }

  @Test
  public void upgradeAndVerifySchemaIntegrityTest() throws Exception {
    initializeClientAndCleanDatabase();
    final OptimizeWrapper oldOptimize =
        new OptimizeWrapper(getDatabaseType(), previousVersion, buildDirectory, oldDatabasePort);
    final OptimizeWrapper newOptimize =
        new OptimizeWrapper(getDatabaseType(), currentVersion, buildDirectory, newDatabasePort);

    try {
      // start new optimize to obtain expected schema and settings
      newOptimize.start(getNewOptimizeOutputLogPath());
      newDatabaseSchemaClient.refreshAll();
      saveNewOptimizeDatabaseStatus();
      newOptimize.stop();
      newDatabaseSchemaClient.cleanIndicesAndTemplates();

      // start old optimize to prepare for upgrade
      oldOptimize.start(getOldOptimizeOutputLogPath());
      oldDatabaseSchemaClient.refreshAll();
      oldOptimize.stop();

      // perform snapshot and restore on new es
      oldDatabaseSchemaClient.createSnapshotRepository();
      oldDatabaseSchemaClient.createSnapshotOfOptimizeIndices();
      oldDatabaseSchemaClient.deleteSnapshotRepository();

      newDatabaseSchemaClient.createSnapshotRepository();
      newDatabaseSchemaClient.restoreSnapshot();
      newDatabaseSchemaClient.deleteSnapshot();

      // start a new async snapshot operation to ensure the upgrade is resilient to concurrently
      // running snapshots
      newDatabaseSchemaClient.createAsyncSnapshot();

      // run the upgrade
      newOptimize.startUpgrade(getOptimizeUpdateLogPath());
      newOptimize.waitForUpgradeToFinish(360);

      // stop/delete async snapshot operation as upgrade completed already
      newDatabaseSchemaClient.deleteAsyncSnapshot();

      // start new optimize
      newOptimize.start(getNewOptimizeOutputLogPath());
      newOptimize.stop();

      log.info("Asserting expected index metadata...");
      assertMigratedDatabaseIndicesMatchExpected();
      assertMigratedDatabaseAliasesMatchExpected();
      assertMigratedDatabaseTemplatesMatchExpected();
      log.info("Finished asserting expected index metadata!");
    } finally {
      oldDatabaseSchemaClient.close();
      newDatabaseSchemaClient.close();
      oldOptimize.stop();
      newOptimize.stop();
    }
  }

  protected Integer getOldDatabasePort() {
    return oldDatabasePort;
  }

  protected Integer getNewDatabasePort() {
    return newDatabasePort;
  }

  protected String getBuildDirectory() {
    return buildDirectory;
  }
}
