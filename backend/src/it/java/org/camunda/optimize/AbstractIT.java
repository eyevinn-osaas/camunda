/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize;

import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtension;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtension;
import org.camunda.optimize.test.optimize.AlertClient;
import org.camunda.optimize.test.optimize.CollectionClient;
import org.camunda.optimize.test.optimize.DashboardClient;
import org.camunda.optimize.test.optimize.EventProcessClient;
import org.camunda.optimize.test.optimize.ReportClient;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractIT {

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtension elasticSearchIntegrationTestExtension
    = new ElasticSearchIntegrationTestExtension();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtension engineIntegrationExtension = new EngineIntegrationExtension();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtension embeddedOptimizeExtension = new EmbeddedOptimizeExtension();

  protected CollectionClient collectionClient = new CollectionClient(embeddedOptimizeExtension);
  protected ReportClient reportClient = new ReportClient(embeddedOptimizeExtension);
  protected AlertClient alertClient = new AlertClient(embeddedOptimizeExtension);
  protected DashboardClient dashboardClient = new DashboardClient(embeddedOptimizeExtension);
  protected EventProcessClient eventProcessClient = new EventProcessClient(embeddedOptimizeExtension);
}
