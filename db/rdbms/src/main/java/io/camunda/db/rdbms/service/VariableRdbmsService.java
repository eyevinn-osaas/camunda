/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.service;

import io.camunda.db.rdbms.domain.VariableModel;
import io.camunda.db.rdbms.queue.ContextType;
import io.camunda.db.rdbms.queue.ExecutionQueue;
import io.camunda.db.rdbms.queue.QueueItem;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.sql.VariableMapper.VariableFilter;
import java.util.List;

public class VariableRdbmsService {

  private final ExecutionQueue executionQueue;
  private final VariableMapper variableMapper;

  public VariableRdbmsService(final ExecutionQueue executionQueue, final VariableMapper variableMapper) {
    this.executionQueue = executionQueue;
    this.variableMapper = variableMapper;
  }

  public void save(final VariableModel variable, final long eventPosition) {
    if (!exists(variable.key())) {
      executionQueue.executeInQueue(new QueueItem(
          ContextType.PROCESS_INSTANCE,
          variable.key(),
          "io.camunda.db.rdbms.sql.VariableMapper.insert",
          variable,
          eventPosition
      ));
      variableMapper.insert(variable);
    } else {
      variableMapper.update(variable);
    }
  }

  public boolean exists(final Long key) {
    return variableMapper.exists(key);
  }

  public VariableModel findOne(final Long key) {
    return variableMapper.findOne(key);
  }

  public List<VariableModel> findByProcessInstanceKey(final Long processInstanceKey) {
    return variableMapper.find(new VariableFilter(processInstanceKey));
  }

}
