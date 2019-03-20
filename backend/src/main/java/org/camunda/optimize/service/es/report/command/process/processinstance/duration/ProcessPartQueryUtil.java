package org.camunda.optimize.service.es.report.command.process.processinstance.duration;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.AggregationResultDto;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.scripted.ScriptedMetric;
import org.elasticsearch.search.aggregations.metrics.scripted.ScriptedMetricAggregationBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.scriptedMetric;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;


public class ProcessPartQueryUtil {

  private static final String SCRIPT_AGGREGATION = "scriptAggregation";
  private static final String NESTED_AGGREGATION = "nestedAggregation";
  private static final String TERMS_AGGREGATIONS = "termsAggregations";

  public static AggregationResultDto processProcessPartAggregationOperations(Aggregations aggs) {
    Terms agg = aggs.get(TERMS_AGGREGATIONS);
    DescriptiveStatistics stats = new DescriptiveStatistics();
    long sum = 0L;
    for (Terms.Bucket entry : agg.getBuckets()) {
      Nested nested = entry.getAggregations().get(NESTED_AGGREGATION);
      ScriptedMetric scriptedMetric = nested.getAggregations().get(SCRIPT_AGGREGATION);
      Integer scriptedResult = (Integer) scriptedMetric.aggregation();
      if (scriptedResult != null) {
        sum += scriptedResult;
        stats.addValue(scriptedResult);
      }
    }
    return new AggregationResultDto(
      Math.round(stats.getMin()),
      Math.round(stats.getMax()),
      Math.round(stats.getMean()),
      Math.round(stats.getPercentile(50))
    );
  }

  public static BoolQueryBuilder addProcessPartQuery(BoolQueryBuilder boolQueryBuilder,
                                                     String startFlowNodeId,
                                                     String endFlowNodeId) {
    String termPath = ProcessInstanceType.EVENTS + "." + ProcessInstanceType.ACTIVITY_ID;
    boolQueryBuilder.must(nestedQuery(
      ProcessInstanceType.EVENTS,
      termQuery(termPath, startFlowNodeId),
      ScoreMode.None)
    );
    boolQueryBuilder.must(nestedQuery(
      ProcessInstanceType.EVENTS,
      termQuery(termPath, endFlowNodeId),
      ScoreMode.None)
    );
    return boolQueryBuilder;
  }

  public static AggregationBuilder createProcessPartAggregation(String startFlowNodeId, String endFlowNodeId) {
    Map<String, Object> params = new HashMap<>();
    params.put("_agg", new HashMap<>());
    params.put("startFlowNodeId", startFlowNodeId);
    params.put("endFlowNodeId", endFlowNodeId);

    ScriptedMetricAggregationBuilder findStartAndEndDatesForEvents = scriptedMetric(SCRIPT_AGGREGATION)
      .initScript(createInitScript())
      .mapScript(createMapScript())
      .combineScript(createCombineScript())
      .reduceScript(getReduceScript())
      .params(params);
    NestedAggregationBuilder searchThroughTheEvents =
      nested(NESTED_AGGREGATION, ProcessInstanceType.EVENTS);
    return
      terms(TERMS_AGGREGATIONS)
      .field(ProcessInstanceType.PROCESS_INSTANCE_ID)
      .subAggregation(
        searchThroughTheEvents
          .subAggregation(
            findStartAndEndDatesForEvents
        )
      );
  }

  private static Script createInitScript() {
    return new Script("params._agg.starts = []; params._agg.ends = []");
  }

  private static Script createMapScript() {
    return new Script(
      "if(doc['events.activityId'].value == params.startFlowNodeId && " +
          "doc['events.startDate'].value != null && " +
          "doc['events.startDate'].value.getMillis() != 0) {" +
        "long startDateInMillis = doc['events.startDate'].value.getMillis();" +
        "params._agg.starts.add(startDateInMillis);" +
      "} else if(doc['events.activityId'].value == params.endFlowNodeId && " +
          "doc['events.endDate'].value != null && " +
          "doc['events.endDate'].value.getMillis() != 0) {" +
        "long endDateInMillis = doc['events.endDate'].value.getMillis();" +
        "params._agg.ends.add(endDateInMillis);" +
      "}"
    );
  }

  private static Script createCombineScript() {
    return new Script(
        "if (!params._agg.starts.isEmpty() && !params._agg.ends.isEmpty()) {" +
        "long minStart = params._agg.starts.stream().min(Long::compareTo).get(); " +
        "List endsLargerMinStart = params._agg.ends.stream().filter(e -> e >= minStart).collect(Collectors.toList());" +
        "if (!endsLargerMinStart.isEmpty()) {" +
          "long closestEnd = endsLargerMinStart.stream()" +
            ".min(Comparator.comparingDouble(v -> Math.abs(v - minStart))).get();" +
          "return closestEnd-minStart;" +
        "}" +
      "}" +
      "return null;"
    );
  }

  private static Script getReduceScript() {
    return new Script(
      "if (params._aggs.size() == 1) {" +
        "return params._aggs.get(0);" +
      "}" +
      "long sum = 0; " +
      "for (a in params._aggs) { " +
        "if (a != null) {" +
          "sum += a " +
        "}" +
      "} " +
      "return sum / Math.max(1, params._aggs.size());"
    );
  }
}
