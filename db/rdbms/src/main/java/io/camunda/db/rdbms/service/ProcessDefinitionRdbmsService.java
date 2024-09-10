/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.service;

import io.camunda.db.rdbms.domain.ProcessDefinitionModel;
import io.camunda.db.rdbms.queue.ContextType;
import io.camunda.db.rdbms.queue.ExecutionQueue;
import io.camunda.db.rdbms.queue.QueueItem;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;

public class ProcessDefinitionRdbmsService {

  private final ExecutionQueue executionQueue;
  private final ProcessDefinitionMapper processDefinitionMapper;

  public ProcessDefinitionRdbmsService(final ExecutionQueue executionQueue, final ProcessDefinitionMapper processDefinitionMapper) {
    this.executionQueue = executionQueue;
    this.processDefinitionMapper = processDefinitionMapper;
  }

  public void save(final ProcessDefinitionModel processDefinition) {
    executionQueue.executeInQueue(new QueueItem(
        ContextType.PROCESS_INSTANCE,
        processDefinition.processDefinitionKey(),
        "io.camunda.db.rdbms.sql.ProcessDefinitionMapper.insert",
        processDefinition
    ));
  }

  public ProcessDefinitionModel findOne(final Long bpmnProcessId) {
    return processDefinitionMapper.findOne(bpmnProcessId);
  }

}
