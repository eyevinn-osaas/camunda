/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.client.impl.search.query;

import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.flownodeInstanceFilter;
import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.flownodeInstanceSort;
import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.searchRequestPage;

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.search.SearchRequestPage;
import io.camunda.zeebe.client.api.search.filter.FlownodeInstanceFilter;
import io.camunda.zeebe.client.api.search.query.FinalSearchQueryStep;
import io.camunda.zeebe.client.api.search.query.FlownodeInstanceQuery;
import io.camunda.zeebe.client.api.search.response.FlownodeInstance;
import io.camunda.zeebe.client.api.search.response.SearchQueryResponse;
import io.camunda.zeebe.client.api.search.sort.FlownodeInstanceSort;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.search.SearchRequestPageImpl;
import io.camunda.zeebe.client.impl.search.SearchResponseMapper;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.impl.search.sort.FlownodeInstanceSortImpl;
import io.camunda.zeebe.client.protocol.rest.FlownodeInstanceFilterRequest;
import io.camunda.zeebe.client.protocol.rest.FlownodeInstanceSearchQueryRequest;
import io.camunda.zeebe.client.protocol.rest.FlownodeInstanceSearchQueryResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class FlownodeInstanceQueryImpl
    extends TypedSearchRequestPropertyProvider<FlownodeInstanceSearchQueryRequest>
    implements FlownodeInstanceQuery {

  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final FlownodeInstanceSearchQueryRequest request;
  private final RequestConfig.Builder httpRequestConfig;

  public FlownodeInstanceQueryImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new FlownodeInstanceSearchQueryRequest();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchQueryStep<FlownodeInstance> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<SearchQueryResponse<FlownodeInstance>> send() {
    final HttpZeebeFuture<SearchQueryResponse<FlownodeInstance>> result = new HttpZeebeFuture<>();
    httpClient.post(
        "/user-tasks/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        FlownodeInstanceSearchQueryResponse.class,
        SearchResponseMapper::toFlownodeInstanceSearchResponse,
        result);
    return result;
  }

  @Override
  public FlownodeInstanceQuery filter(final FlownodeInstanceFilter value) {
    final FlownodeInstanceFilterRequest filter = provideSearchRequestProperty(value);
    request.setFilter(filter);
    return this;
  }

  @Override
  public FlownodeInstanceQuery filter(final Consumer<FlownodeInstanceFilter> fn) {
    return filter(flownodeInstanceFilter(fn));
  }

  @Override
  public FlownodeInstanceQuery sort(final FlownodeInstanceSort value) {
    final FlownodeInstanceSortImpl sorting = (FlownodeInstanceSortImpl) value;
    request.setSort(sorting.getSearchRequestProperty());
    return this;
  }

  @Override
  public FlownodeInstanceQuery sort(final Consumer<FlownodeInstanceSort> fn) {
    return sort(flownodeInstanceSort(fn));
  }

  @Override
  public FlownodeInstanceQuery page(final SearchRequestPage value) {
    final SearchRequestPageImpl page = (SearchRequestPageImpl) value;
    request.setPage(page.getSearchRequestProperty());
    return this;
  }

  @Override
  public FlownodeInstanceQuery page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected FlownodeInstanceSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
