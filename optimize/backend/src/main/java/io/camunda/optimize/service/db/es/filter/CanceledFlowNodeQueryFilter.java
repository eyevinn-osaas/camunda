/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_CANCELED;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.CanceledFlowNodeFilterDataDto;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CanceledFlowNodeQueryFilter implements QueryFilter<CanceledFlowNodeFilterDataDto> {

  @Override
  public void addFilters(
      final BoolQueryBuilder query,
      final List<CanceledFlowNodeFilterDataDto> flowNodeFilter,
      final FilterContext filterContext) {
    List<QueryBuilder> filters = query.filter();
    for (CanceledFlowNodeFilterDataDto executedFlowNode : flowNodeFilter) {
      filters.add(createFilterQueryBuilder(executedFlowNode));
    }
  }

  private QueryBuilder createFilterQueryBuilder(CanceledFlowNodeFilterDataDto flowNodeFilter) {
    BoolQueryBuilder boolQueryBuilder = boolQuery();
    final BoolQueryBuilder isCanceledQuery =
        boolQuery()
            .must(existsQuery(nestedCanceledFieldLabel()))
            .must(termQuery(nestedCanceledFieldLabel(), true));
    for (String value : flowNodeFilter.getValues()) {
      boolQueryBuilder.should(
          nestedQuery(
              FLOW_NODE_INSTANCES,
              boolQuery()
                  .must(isCanceledQuery)
                  .must(termQuery(nestedActivityIdFieldLabel(), value)),
              ScoreMode.None));
    }
    return boolQueryBuilder;
  }

  private String nestedActivityIdFieldLabel() {
    return FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID;
  }

  private String nestedCanceledFieldLabel() {
    return FLOW_NODE_INSTANCES + "." + FLOW_NODE_CANCELED;
  }
}
