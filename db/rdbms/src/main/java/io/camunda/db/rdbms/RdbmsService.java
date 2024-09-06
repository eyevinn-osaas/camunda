/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.db.rdbms.queue.ExecutionQueue;
import io.camunda.db.rdbms.service.ProcessRdbmsService;
import io.camunda.db.rdbms.service.VariableRdbmsService;

/**
 * A holder for all rdbms services
 */
public class RdbmsService {


  private final ExecutionQueue executionQueue;
  private final ProcessRdbmsService processRdbmsService;
  private final VariableRdbmsService variableRdbmsService;

  public RdbmsService(final ExecutionQueue executionQueue,
      final ProcessRdbmsService processRdbmsService,
    final VariableRdbmsService variableRdbmsService
   ) {
    this.executionQueue = executionQueue;
    this.processRdbmsService = processRdbmsService;
    this.variableRdbmsService = variableRdbmsService;
  }

  public ProcessRdbmsService getProcessRdbmsService() {
    return processRdbmsService;
  }

  public VariableRdbmsService getVariableRdbmsService() {
    return variableRdbmsService;
  }

  public ExecutionQueue executionQueue() {
    return this.executionQueue;
  }
}
