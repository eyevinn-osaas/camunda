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
package io.camunda.zeebe.client.impl.command;

import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.command.TopologyRequestStep1;
import io.camunda.zeebe.client.api.response.Topology;
import io.camunda.zeebe.client.impl.RetriableClientFutureImpl;
import io.camunda.zeebe.client.impl.response.TopologyImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyResponse;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class TopologyRequestImpl implements TopologyRequestStep1 {

  private final GatewayStub asyncStub;
  private final Predicate<Throwable> retryPredicate;
  private Duration requestTimeout;
  private boolean useRest;

  public TopologyRequestImpl(
      final GatewayStub asyncStub,
      final Duration requestTimeout,
      final Predicate<Throwable> retryPredicate) {
    this.asyncStub = asyncStub;
    this.requestTimeout = requestTimeout;
    this.retryPredicate = retryPredicate;
    useRest = false;
  }

  @Override
  public TopologyRequestStep1 useRest() {
    return this;
  }

  @Override
  public TopologyRequestStep1 useGrpc() {
    useRest = false;
    return this;
  }

  @Override
  public FinalCommandStep<Topology> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<Topology> send() {
    final TopologyRequest request = TopologyRequest.getDefaultInstance();

    final RetriableClientFutureImpl<Topology, TopologyResponse> future =
        new RetriableClientFutureImpl<>(
            TopologyImpl::new, retryPredicate, streamObserver -> send(request, streamObserver));

    send(request, future);

    return future;
  }

  private void send(
      final TopologyRequest request, final StreamObserver<TopologyResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .topology(request, streamObserver);
  }
}
