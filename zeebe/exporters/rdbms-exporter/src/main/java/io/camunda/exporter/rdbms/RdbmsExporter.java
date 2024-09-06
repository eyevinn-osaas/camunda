/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.domain.ProcessInstanceModel;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.exporter.context.ExporterContext;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RdbmsExporter implements Exporter {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsExporter.class);

  private Controller controller;
  private long lastPosition = -1;

  private RdbmsService rdbmsService;

  @Override
  public void configure(final Context context) {
    ((ExporterContext) context).getSpringBrokerBridge()
        .flatMap(SpringBrokerBridge::getRdbmsService)
        .ifPresent(service -> rdbmsService = service);
    LOG.info("RDBMS Exporter configured!");
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;

    LOG.info("Exporter opened");
  }

  @Override
  public void close() {
    try {
      updateLastExportedPosition();
    } catch (final Exception e) {
      LOG.warn("Failed to flush records before closing exporter.", e);
    }

    LOG.info("Exporter closed");
  }

  @Override
  public void export(final Record<?> record) {
    LOG.debug("Exporting record {}-{} - {}:{}", record.getPartitionId(), record.getPosition(),
        record.getValueType(), record.getIntent());

    if (record.getValueType() == ValueType.PROCESS_INSTANCE_CREATION
        && record.getIntent() == ProcessInstanceCreationIntent.CREATED) {
      final ProcessInstanceCreationRecordValue value = (ProcessInstanceCreationRecordValue) record.getValue();

      LOG.debug("Export Process Created event: {}", value.getBpmnProcessId());
      rdbmsService.processRdbmsService().save(
          new ProcessInstanceModel(
              value.getProcessInstanceKey(),
              value.getBpmnProcessId(),
              value.getProcessDefinitionKey(),
              value.getTenantId()
          )
      );
    }

    lastPosition = record.getPosition();
    updateLastExportedPosition();
  }

  private void updateLastExportedPosition() {
    controller.updateLastExportedRecordPosition(lastPosition);
  }

}
