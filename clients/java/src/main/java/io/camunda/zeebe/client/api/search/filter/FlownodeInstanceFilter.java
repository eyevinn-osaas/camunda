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
package io.camunda.zeebe.client.api.search.filter;

import io.camunda.zeebe.client.api.search.query.TypedSearchQueryRequest.SearchRequestFilter;

public interface FlownodeInstanceFilter extends SearchRequestFilter {

  FlownodeInstanceFilter key(final long value);

  FlownodeInstanceFilter processDefinitionKey(final long value);

  FlownodeInstanceFilter processInstanceKey(final long value);

  FlownodeInstanceFilter flowNodeId(final String value);

  FlownodeInstanceFilter flowNodeName(final String value);

  FlownodeInstanceFilter state(final String value);

  FlownodeInstanceFilter type(final String value);

  FlownodeInstanceFilter incident(final boolean value);

  FlownodeInstanceFilter incidentKey(final long value);

  FlownodeInstanceFilter treePath(final String value);

  FlownodeInstanceFilter tenantId(final String value);
}
