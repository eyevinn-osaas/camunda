/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.domain.ProcessDefinitionModel;
import io.camunda.db.rdbms.service.ProcessDefinitionRdbmsService;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;

public class ProcessDeploymentExportHandler implements RdbmsExportHandler<ProcessRecord> {

  private final ProcessDefinitionRdbmsService processDefinitionRdbmsService;

  public ProcessDeploymentExportHandler(final ProcessDefinitionRdbmsService processDefinitionRdbmsService) {
    this.processDefinitionRdbmsService = processDefinitionRdbmsService;
  }

  @Override
  public boolean canExport(final Record<ProcessRecord> record) {
    return record.getIntent() == ProcessIntent.CREATED;
  }

  @Override
  public void export(final Record<ProcessRecord> record) {
    final ProcessRecord value = record.getValue();
    processDefinitionRdbmsService.save(map(value));
  }

  private ProcessDefinitionModel map(final ProcessRecord value) {
    return new ProcessDefinitionModel(
        value.getProcessDefinitionKey(),
        value.getBpmnProcessId(),
        value.getTenantId(),
        value.getVersion(),
        value.getVersionTag()
    );
  }
}
