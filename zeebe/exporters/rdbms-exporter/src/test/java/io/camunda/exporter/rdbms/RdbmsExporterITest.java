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
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base Class for all tests ... Uses H2
 */
@SpringBootTest(classes = {RdbmsTestConfiguration.class})
@Testcontainers
class RdbmsExporterITest {

  private final ExporterTestController controller = new ExporterTestController();

  private final RdbmsExporter exporter = new RdbmsExporter();

  private final ProtocolFactory factory = new ProtocolFactory();

  @Autowired
  private RdbmsService rdbmsService;

  @BeforeEach
  void setUp() {
    exporter.configure(
        new ExporterContext(
            null,
            null,
            0,
            null,
            null,
            new SpringBrokerBridge(rdbmsService)
        )
    );
    exporter.open(controller);
  }

  @Test
  public void shouldExportProcessInstance() {
    // given
    final var processInstanceRecord = factory.generateRecordWithIntent(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when
    exporter.export(processInstanceRecord);
    // and we do a manual flush
    rdbmsService.executionQueue().flush();

    // then
    final var key = ((ProcessInstanceRecordValue) processInstanceRecord.getValue()).getProcessInstanceKey();
    final var processInstance = rdbmsService.getProcessRdbmsService().findOne(key);
    Assertions.assertThat(processInstance).isNotNull();
  }
}
