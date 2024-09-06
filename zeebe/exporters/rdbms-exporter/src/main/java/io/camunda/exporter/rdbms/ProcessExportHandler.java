/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.domain.ProcessInstanceModel;
import io.camunda.db.rdbms.service.ProcessRdbmsService;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;

public class ProcessExportHandler implements RdbmsExportHandler<ProcessInstanceCreationRecord> {

  private final ProcessRdbmsService processRdbmsService;

  public ProcessExportHandler(final ProcessRdbmsService processRdbmsService) {
    this.processRdbmsService = processRdbmsService;
  }

  @Override
  public boolean canExport(final Record<ProcessInstanceCreationRecord> record) {
    return record.getIntent() == ProcessInstanceCreationIntent.CREATED;
  }

  @Override
  public void export(final Record<ProcessInstanceCreationRecord> record) {
    final ProcessInstanceCreationRecord value = record.getValue();
    processRdbmsService.save(map(value));
  }

  private ProcessInstanceModel map(final ProcessInstanceCreationRecord value) {
    return new ProcessInstanceModel(
        value.getProcessInstanceKey(),
        value.getBpmnProcessId(),
        value.getProcessDefinitionKey(),
        value.getTenantId()
    );
  }
}
