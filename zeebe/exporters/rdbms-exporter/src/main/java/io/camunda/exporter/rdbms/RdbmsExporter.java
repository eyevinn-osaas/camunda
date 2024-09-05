/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.exporter.rdbms.sql.MapperHolder;
import io.camunda.exporter.rdbms.sql.ProcessInstanceMapper;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RdbmsExporter implements Exporter {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsExporter.class);

  private Controller controller;
  private long lastPosition = -1;
  private ProcessInstanceMapper processInstanceMapper;

  @Override
  public void configure(final Context context) {
    LOG.info("RDBMS Exporter configured!");
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;

    scheduleDelayedFlush();

    LOG.info("Exporter opened");
  }

  @Override
  public void close() {
    try {
      flush();
      updateLastExportedPosition();
    } catch (final Exception e) {
      LOG.warn("Failed to flush records before closing exporter.", e);
    }

    LOG.info("Exporter closed");
  }

  @Override
  public void export(final Record<?> record) {
    LOG.debug("EXPORT IT!");

    if (record.getValueType() == ValueType.PROCESS_INSTANCE
        && ((ProcessInstanceRecordValue) record.getValue()).getBpmnElementType() == BpmnElementType.PROCESS
        && record.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATED) {
      ProcessInstanceRecordValue value = (ProcessInstanceRecordValue) record.getValue();

      MapperHolder.PROCESS_INSTANCE_MAPPER.insertProcessInstance(
          Long.toString(value.getProcessInstanceKey()));
    }

    lastPosition = record.getPosition();
    if (shouldFlush()) {
      flush();
      // Update the record counters only after the flush was successful. If the synchronous flush
      // fails then the exporter will be invoked with the same record again.
      updateLastExportedPosition();
    }
  }


  private boolean shouldFlush() {
    // FIXME should compare against both batch size and memory limit
    return true;
  }

  private void scheduleDelayedFlush() {
    controller.scheduleCancellableTask(Duration.ofSeconds(5), this::flushAndReschedule);
  }

  private void flushAndReschedule() {
    try {
      flush();
      updateLastExportedPosition();
    } catch (final Exception e) {
      LOG.warn("Unexpected exception occurred on periodically flushing bulk, will retry later.", e);
    }
    scheduleDelayedFlush();
  }

  private void flush() {
    LOG.debug("FLUSH IT!");
  }

  private void updateLastExportedPosition() {
    controller.updateLastExportedRecordPosition(lastPosition);
  }

}
