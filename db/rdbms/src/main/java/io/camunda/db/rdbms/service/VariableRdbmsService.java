/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.service;

import io.camunda.db.rdbms.domain.VariableModel;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.sql.VariableMapper.VariableFilter;
import java.util.List;

public class VariableRdbmsService {

  private final VariableMapper variableMapper;

  public VariableRdbmsService(final VariableMapper variableMapper) {
    this.variableMapper = variableMapper;
  }

  public void save(final VariableModel variable) {
    if (!exists(variable.key())) {
      variableMapper.insert(variable);
    } else {
      // TODO Update
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
