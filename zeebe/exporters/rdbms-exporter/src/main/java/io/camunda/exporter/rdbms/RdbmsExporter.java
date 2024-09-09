/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.exporter.context.ExporterContext;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RdbmsExporter implements Exporter {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsExporter.class);

  private final HashMap<ValueType, RdbmsExportHandler> registeredHandlers = new HashMap<>();

  private Controller controller;
  private RdbmsService rdbmsService;

  private long lastPosition = -1;

  @Override
  public void configure(final Context context) {
    ((ExporterContext) context).getSpringBrokerBridge()
        .flatMap(SpringBrokerBridge::getRdbmsService)
        .ifPresent(service -> {
          rdbmsService = service;
          registerHandler();
        });

    LOG.info("[RDBMS Exporter] RDBMS Exporter configured!");
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;

    LOG.info("[RDBMS Exporter] Exporter opened");
    this.rdbmsService.executionQueue().registerFlushListener(this::updatePosition);

    LOG.info("Exporter opened");
  }

  @Override
  public void close() {
    try {
      rdbmsService.executionQueue().flush();
    } catch (final Exception e) {
      LOG.warn("[RDBMS Exporter] Failed to flush records before closing exporter.", e);
    }

    LOG.info("[RDBMS Exporter] Exporter closed");
  }

  @Override
  public void export(final Record<?> record) {
    LOG.debug("[RDBMS Exporter] Exporting record {}-{} - {}:{}", record.getPartitionId(),
        record.getPosition(),
        record.getValueType(), record.getIntent());

    if (registeredHandlers.containsKey(record.getValueType())) {
      final var handler = registeredHandlers.get(record.getValueType());
      lastPosition = record.getPosition();
      if (handler.canExport(record)) {
        LOG.debug("[RDBMS Exporter] Exporting record {} with handler {}", record.getValue(),
            handler.getClass());
        handler.export(record);
      } else {
        LOG.debug("[RDBMS Exporter] Handler {} can not export record {}", handler.getClass(),
            record.getValueType());
      }
    } else {
      LOG.debug("[RDBMS Exporter] No registered handler found for {}", record.getValueType());
    }
  }

  private void registerHandler() {
    registeredHandlers.put(ValueType.PROCESS_INSTANCE, new ProcessInstanceExportHandler(rdbmsService.getProcessRdbmsService()));
    registeredHandlers.put(ValueType.VARIABLE, new VariableExportHandler(rdbmsService.getVariableRdbmsService()));
  }

  private void updatePosition() {
    LOG.debug("Updating position to {}", lastPosition);
    controller.updateLastExportedRecordPosition(lastPosition);
  }
}
