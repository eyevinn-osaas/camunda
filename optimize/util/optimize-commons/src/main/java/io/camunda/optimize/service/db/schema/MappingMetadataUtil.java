/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema;

import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_INDEX_PREFIX;

import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import io.camunda.optimize.service.db.es.schema.index.DecisionInstanceIndexES;
import io.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import io.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import io.camunda.optimize.service.db.os.schema.index.DecisionInstanceIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ProcessInstanceIndexOS;
import io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;

public class MappingMetadataUtil {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(MappingMetadataUtil.class);
  private final DatabaseClient dbClient;
  private final boolean isElasticSearchClient;

  public MappingMetadataUtil(final DatabaseClient dbClient) {
    this.dbClient = dbClient;
    isElasticSearchClient = dbClient instanceof OptimizeElasticsearchClient;
  }

  public List<IndexMappingCreator<?>> getAllMappings(final String indexPrefix) {
    final List<IndexMappingCreator<?>> allMappings = new ArrayList<>();
    allMappings.addAll(getAllNonDynamicMappings());
    allMappings.addAll(getAllDynamicMappings(indexPrefix));
    return allMappings;
  }

  private Collection<? extends IndexMappingCreator<?>> getAllNonDynamicMappings() {
    return isElasticSearchClient
        ? ElasticSearchSchemaManager.getAllNonDynamicMappings()
        : OpenSearchSchemaManager.getAllNonDynamicMappings();
  }

  public List<IndexMappingCreator<?>> getAllDynamicMappings(final String indexPrefix) {
    final List<IndexMappingCreator<?>> dynamicMappings = new ArrayList<>();
    dynamicMappings.addAll(retrieveAllProcessInstanceIndices(indexPrefix));
    dynamicMappings.addAll(retrieveAllDecisionInstanceIndices());
    return dynamicMappings;
  }

  public List<String> retrieveProcessInstanceIndexIdentifiers(final String configuredIndexPrefix) {
    final Map<String, Set<String>> aliases;
    final String fullIndexPrefix = configuredIndexPrefix + "-" + PROCESS_INSTANCE_INDEX_PREFIX;
    try {
      aliases = dbClient.getAliasesForIndexPattern(fullIndexPrefix + "*");
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          "Failed retrieving aliases for dynamic index prefix " + fullIndexPrefix, e);
    }
    return aliases.entrySet().stream()
        .flatMap(aliasMetadataPerIndex -> aliasMetadataPerIndex.getValue().stream())
        .filter(fullAliasName -> fullAliasName.contains(fullIndexPrefix))
        .map(
            fullAliasName ->
                fullAliasName.substring(
                    fullAliasName.indexOf(fullIndexPrefix) + fullIndexPrefix.length()))
        .toList();
  }

  private List<? extends DecisionInstanceIndex<?>> retrieveAllDecisionInstanceIndices() {
    return retrieveAllDynamicIndexKeysForPrefix(DECISION_INSTANCE_INDEX_PREFIX).stream()
        .map(
            key ->
                isElasticSearchClient
                    ? new DecisionInstanceIndexES(key)
                    : new DecisionInstanceIndexOS(key))
        .toList();
  }

  private List<? extends ProcessInstanceIndex<?>> retrieveAllProcessInstanceIndices(
      final String indexPrefix) {
    return retrieveProcessInstanceIndexIdentifiers(indexPrefix).stream()
        .map(
            key ->
                isElasticSearchClient
                    ? new ProcessInstanceIndexES(key)
                    : new ProcessInstanceIndexOS(key))
        .toList();
  }

  private List<String> retrieveAllDynamicIndexKeysForPrefix(final String dynamicIndexPrefix) {
    try {
      return dbClient.getAllIndicesForAlias(dynamicIndexPrefix + "*").stream()
          .map(
              fullAliasName ->
                  fullAliasName.substring(
                      fullAliasName.indexOf(dynamicIndexPrefix) + dynamicIndexPrefix.length()))
          .toList();
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          "Failed retrieving aliases for dynamic index prefix " + dynamicIndexPrefix, e);
    }
  }
}
