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
package io.camunda.zeebe.protocol.record.value;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableProtocol(builder = ImmutableCommandDistributionRecordValue.Builder.class)
public interface CommandDistributionRecordValue extends RecordValue {

  /**
   * @return the partition where the record should be distributed to
   */
  int getPartitionId();

  /**
   * @return the queue id for this distribution or null if the queue id is not set.
   */
  String getQueueId();

  /**
   * @return the wrapped record value type
   */
  ValueType getValueType();

  /**
   * @return the wrapped intent
   */
  Intent getIntent();

  /**
   * @return the wrapped record value
   */
  RecordValue getCommandValue();
}
