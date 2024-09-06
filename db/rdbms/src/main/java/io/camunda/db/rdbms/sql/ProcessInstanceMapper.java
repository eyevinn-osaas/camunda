/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.domain.ProcessInstanceModel;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProcessInstanceMapper {

  @Insert("INSERT INTO PROCESS_INSTANCE (id) VALUES(#{processInstanceKey})")
  void insertProcessInstance(@Param("processInstanceKey") String processInstanceKey);

  @Select("SELECT id FROM PROCESS_INSTANCE WHERE id = #{processInstanceKey}")
  ProcessInstanceModel findOne(@Param("processInstanceKey") String processInstanceKey);
}
