/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import io.camunda.optimize.service.db.reader.BackupReader;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class BackupReaderOS implements BackupReader {

  @Override
  public void validateRepositoryExistsOrFail() {
    log.debug("Functionality not implemented for OpenSearch");
  }

  @Override
  public void validateNoDuplicateBackupId(final Long backupId) {
    log.debug("Functionality not implemented for OpenSearch");
  }

  @Override
  public Map<Long, List<SnapshotInfo>> getAllOptimizeSnapshotsByBackupId() {
    log.debug("Functionality not implemented for OpenSearch");
    return null;
  }

  @Override
  public List<SnapshotInfo> getAllOptimizeSnapshots() {
    log.debug("Functionality not implemented for OpenSearch");
    return new ArrayList<>();
  }

  @Override
  public List<SnapshotInfo> getOptimizeSnapshotsForBackupId(final Long backupId) {
    log.debug("Functionality not implemented for OpenSearch");
    return new ArrayList<>();
  }
}
