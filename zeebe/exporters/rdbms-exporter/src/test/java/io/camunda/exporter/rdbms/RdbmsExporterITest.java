/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.domain.VariableModel;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.exporter.context.ExporterContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
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
    var processInstanceRecord = factory.generateRecordWithIntent(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATING);
    // TODO ... DIRTY!!!!!
    while (true) {
      processInstanceRecord = factory.generateRecordWithIntent(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATING);
      final ProcessInstanceRecordValue value = (ProcessInstanceRecordValue) processInstanceRecord.getValue();
      if (value.getBpmnElementType() == BpmnElementType.PROCESS) {
        break;
      }
    }

    // when
    exporter.export(processInstanceRecord);
    // and we do a manual flush
    rdbmsService.executionQueue().flush();

    // then
    final var key = ((ProcessInstanceRecordValue) processInstanceRecord.getValue()).getProcessInstanceKey();
    final var processInstance = rdbmsService.getProcessInstanceRdbmsService().findOne(key);
    assertThat(processInstance).isNotNull();
  }

  @Test
  public void shouldExportProcessInstanceAndVariables() {
    // given
    var processInstanceRecord = factory.generateRecordWithIntent(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATING);
    // TODO ... DIRTY!!!!!
    while (true) {
      processInstanceRecord = factory.generateRecordWithIntent(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATING);
      final ProcessInstanceRecordValue value = (ProcessInstanceRecordValue) processInstanceRecord.getValue();
      if (value.getBpmnElementType() == BpmnElementType.PROCESS) {
        break;
      }
    }
    final Record<RecordValue> variableCreated = factory.generateRecordWithIntent(ValueType.VARIABLE, VariableIntent.CREATED);
    final List<Record<RecordValue>> recordList = List.of(
        factory.generateRecord(ValueType.PROCESS),
        factory.generateRecord(ValueType.VARIABLE),
        factory.generateRecord(ValueType.USER_TASK),
        factory.generateRecord(ValueType.JOB),
        processInstanceRecord,
        variableCreated
    );

    // when
    recordList.forEach(record -> exporter.export(record));
    // and we do a manual flush
    rdbmsService.executionQueue().flush();

    // then
    final var key = ((ProcessInstanceRecordValue) processInstanceRecord.getValue()).getProcessInstanceKey();
    final var processInstance = rdbmsService.getProcessInstanceRdbmsService().findOne(key);
    assertThat(processInstance).isNotNull();

    final VariableModel variable = rdbmsService.getVariableRdbmsService().findOne(variableCreated.getKey());
    final VariableRecordValue variableRecordValue = (VariableRecordValue) variableCreated.getValue();
    assertThat(variable).isNotNull();
    assertThat(variable.value()).isEqualTo(variableRecordValue.getValue());
  }
}
