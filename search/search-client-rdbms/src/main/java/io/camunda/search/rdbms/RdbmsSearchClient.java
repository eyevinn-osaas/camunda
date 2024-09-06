/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.rdbms;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.zeebe.util.Either;
import java.util.List;

public class RdbmsSearchClient implements CamundaSearchClient {

  private final RdbmsService rdbmsService;

  public RdbmsSearchClient(final RdbmsService rdbmsService) {
    this.rdbmsService = rdbmsService;
  }

  @Override
  public <T> Either<Exception, SearchQueryResponse<T>> search(
      final SearchQueryRequest searchRequest, final Class<T> documentClass) {
    if (searchRequest.index().stream().anyMatch(s -> s.startsWith("operate-list-view"))) {
      final var bpmnProcessId = getBpmnProcessId(searchRequest.query());
      if (bpmnProcessId != null) {
        final var processInstance = rdbmsService.getProcessRdbmsService().findOne(42L);

        return Either.right(new SearchQueryResponse(1, "bla", List.of(
            new SearchQueryHit.Builder()
                .source(new ProcessInstanceEntity(
                    null, null, null, "42",
                    null, null, null,
                    null, null, null,
                    null, null, null,
                    null, null, null
                ))
                .build()
        )));
      }
    }

    return null;
  }

  @Override
  public void close() throws Exception {

  }

  public String getBpmnProcessId(final SearchQuery searchQuery) {
    if (searchQuery.queryOption() instanceof final SearchTermQuery searchTermQuery) {
      if (searchTermQuery.field().equalsIgnoreCase("bpmnProcessId")) {
        return searchTermQuery.value().stringValue();
      } else {
        return null;
      }
    } else if (searchQuery.queryOption() instanceof final SearchBoolQuery searchBoolQuery) {
      for (final SearchQuery sq : searchBoolQuery.must()) {
        final var term = getBpmnProcessId(sq);
        if (term != null) {
          return term;
        }
      }
    }

    return null;
  }
}
