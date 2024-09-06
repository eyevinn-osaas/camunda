/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.service;

import io.camunda.db.rdbms.domain.ProcessInstanceModel;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;

public class ProcessRdbmsService {

  private final ProcessInstanceMapper processInstanceMapper;

  public ProcessRdbmsService(final ProcessInstanceMapper processInstanceMapper) {
    this.processInstanceMapper = processInstanceMapper;
  }

  public void save(final ProcessInstanceModel processInstance) {
    processInstanceMapper.insert(processInstance);
  }

  public ProcessInstanceModel findOne(final Long processInstanceKey) {
    return processInstanceMapper.findOne(processInstanceKey);
  }

}
