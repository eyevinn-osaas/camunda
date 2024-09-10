/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.START_DATE;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.service.db.es.filter.util.DateFilterQueryUtilES;
import io.camunda.optimize.service.db.filter.FilterContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class InstanceStartDateQueryFilter implements QueryFilterES<DateFilterDataDto<?>> {

  @Override
  public void addFilters(
      final BoolQueryBuilder query,
      final List<DateFilterDataDto<?>> filter,
      final FilterContext filterContext) {
    DateFilterQueryUtilES.addFilters(query, filter, START_DATE, filterContext.getTimezone());
  }
}
