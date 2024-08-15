/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.pagination;

import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.QueryParam;

public class PaginationRequestDto {

  public static final String LIMIT_PARAM = "limit";
  public static final String OFFSET_PARAM = "offset";

  @QueryParam(LIMIT_PARAM)
  @Min(0)
  @Max(MAX_RESPONSE_SIZE_LIMIT)
  protected Integer limit;

  @QueryParam(OFFSET_PARAM)
  @Min(0)
  protected Integer offset;

  public PaginationRequestDto(@Min(0) @Max(MAX_RESPONSE_SIZE_LIMIT) final Integer limit,
      @Min(0) final Integer offset) {
    this.limit = limit;
    this.offset = offset;
  }

  public PaginationRequestDto() {
  }

  public @Min(0) @Max(MAX_RESPONSE_SIZE_LIMIT) Integer getLimit() {
    return limit;
  }

  public void setLimit(@Min(0) @Max(MAX_RESPONSE_SIZE_LIMIT) final Integer limit) {
    this.limit = limit;
  }

  public @Min(0) Integer getOffset() {
    return offset;
  }

  public void setOffset(@Min(0) final Integer offset) {
    this.offset = offset;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof PaginationRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $limit = getLimit();
    result = result * PRIME + ($limit == null ? 43 : $limit.hashCode());
    final Object $offset = getOffset();
    result = result * PRIME + ($offset == null ? 43 : $offset.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof PaginationRequestDto)) {
      return false;
    }
    final PaginationRequestDto other = (PaginationRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$limit = getLimit();
    final Object other$limit = other.getLimit();
    if (this$limit == null ? other$limit != null : !this$limit.equals(other$limit)) {
      return false;
    }
    final Object this$offset = getOffset();
    final Object other$offset = other.getOffset();
    if (this$offset == null ? other$offset != null : !this$offset.equals(other$offset)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "PaginationRequestDto(limit=" + getLimit() + ", offset=" + getOffset() + ")";
  }
}
