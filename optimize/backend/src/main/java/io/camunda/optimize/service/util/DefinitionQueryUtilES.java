/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static io.camunda.optimize.service.util.DefinitionVersionHandlingUtil.isDefinitionVersionSetToAll;
import static io.camunda.optimize.service.util.DefinitionVersionHandlingUtil.isDefinitionVersionSetToLatest;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.google.common.collect.ImmutableList;
import io.camunda.optimize.dto.optimize.ReportConstants;
import io.camunda.optimize.service.db.schema.index.AbstractInstanceIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class DefinitionQueryUtilES {

  private DefinitionQueryUtilES() {}

  public static BoolQueryBuilder createDefinitionQuery(
      final String definitionKey, final List<String> tenantIds, final AbstractInstanceIndex type) {
    return createDefinitionQuery(
        definitionKey,
        ImmutableList.of(ReportConstants.ALL_VERSIONS),
        tenantIds,
        type,
        // not relevant
        s -> "");
  }

  public static BoolQueryBuilder createDefinitionQuery(
      final Map<String, Set<String>> definitionKeyToTenantsMap,
      final String definitionKeyFieldName,
      final String tenantKeyFieldName) {
    final BoolQueryBuilder query = boolQuery().minimumShouldMatch(1);
    definitionKeyToTenantsMap.forEach(
        (definitionKey, tenantIds) ->
            query.should(
                boolQuery()
                    .must(termQuery(definitionKeyFieldName, definitionKey))
                    .must(createTenantIdQuery(tenantKeyFieldName, new ArrayList<>(tenantIds)))));
    return query;
  }

  public static BoolQueryBuilder createDefinitionQuery(
      final String definitionKey,
      final List<String> definitionVersions,
      final List<String> tenantIds,
      final AbstractInstanceIndex type,
      final UnaryOperator<String> getLatestVersionToKey) {
    final BoolQueryBuilder query = boolQuery();
    query.must(createTenantIdQuery(type.getTenantIdFieldName(), tenantIds));
    query.must(termQuery(type.getDefinitionKeyFieldName(), definitionKey));
    if (isDefinitionVersionSetToLatest(definitionVersions)) {
      query.must(
          termsQuery(
              type.getDefinitionVersionFieldName(), getLatestVersionToKey.apply(definitionKey)));
    } else if (!isDefinitionVersionSetToAll(definitionVersions)) {
      query.must(termsQuery(type.getDefinitionVersionFieldName(), definitionVersions));
    } else if (definitionVersions.isEmpty()) {
      // if no version is set just return empty results
      query.mustNot(matchAllQuery());
    }
    return query;
  }

  public static QueryBuilder createTenantIdQuery(
      final String tenantField, final List<String> tenantIds) {
    final AtomicBoolean includeNotDefinedTenant = new AtomicBoolean(false);
    final List<String> tenantIdTerms =
        tenantIds.stream()
            .peek(
                id -> {
                  if (id == null) {
                    includeNotDefinedTenant.set(true);
                  }
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    final BoolQueryBuilder tenantQueryBuilder = boolQuery().minimumShouldMatch(1);
    if (!tenantIdTerms.isEmpty()) {
      tenantQueryBuilder.should(termsQuery(tenantField, tenantIdTerms));
    }
    if (includeNotDefinedTenant.get()) {
      tenantQueryBuilder.should(boolQuery().mustNot(existsQuery(tenantField)));
    }
    if (tenantIdTerms.isEmpty() && !includeNotDefinedTenant.get()) {
      // All tenants have been deselected and therefore we should not return any data.
      // This query ensures that the condition never holds for any data.
      tenantQueryBuilder.mustNot(matchAllQuery());
    }

    return tenantQueryBuilder;
  }
}
