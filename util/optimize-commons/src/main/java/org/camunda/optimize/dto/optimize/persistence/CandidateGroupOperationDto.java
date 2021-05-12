/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.io.Serializable;
import java.time.OffsetDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@FieldNameConstants
public class CandidateGroupOperationDto implements OptimizeDto, Serializable {

  @EqualsAndHashCode.Include
  private String id;

  private String groupId;
  private String operationType;
  private OffsetDateTime timestamp;
}
