package org.camunda.optimize.dto.optimize.query.report.single.configuration;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.AVERAGE_AGGREGATION_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.MAX_AGGREGATION_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.MEDIAN_AGGREGATION_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.MIN_AGGREGATION_TYPE;

public enum AggregationType {
  AVERAGE(AVERAGE_AGGREGATION_TYPE),
  MIN(MIN_AGGREGATION_TYPE),
  MAX(MAX_AGGREGATION_TYPE),
  MEDIAN(MEDIAN_AGGREGATION_TYPE),
  ;

  private final String id;

  AggregationType(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return getId();
  }
}
