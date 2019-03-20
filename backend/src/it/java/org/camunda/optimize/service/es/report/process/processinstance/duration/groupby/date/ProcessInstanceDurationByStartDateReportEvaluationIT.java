package org.camunda.optimize.service.es.report.process.processinstance.duration.groupby.date;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.AggregationResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_DUR_GROUP_BY_START_DATE;
import static org.camunda.optimize.test.util.ProcessVariableFilterUtilHelper.createBooleanVariableFilter;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

public class ProcessInstanceDurationByStartDateReportEvaluationIT {

  private static final String PROCESS_DEFINITION_KEY = "123";
  private static final String TEST_ACTIVITY = "testActivity";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();


  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
    .around(engineRule)
    .around(embeddedOptimizeRule)
    .around(engineDatabaseRule);

  @Test
  public void simpleReportEvaluation() throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 1L);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .build();
    ProcessDurationReportMapResultDto resultDto = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = resultDto.getData();
    assertThat(resultDto.getProcessInstanceCount(), is(1L));
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));

    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.START_DATE));
    StartDateGroupByDto startDateGroupByDto = (StartDateGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(startDateGroupByDto.getValue().getUnit(), is(GroupByDateUnit.DAY));
    assertThat(resultDto.getResult(), is(notNullValue()));
    assertThat(resultDto.getResult().size(), is(1));
    Map<String, AggregationResultDto> resultMap = resultDto.getResult();
    ZonedDateTime startOfToday = truncateToStartOfUnit(startDate, ChronoUnit.DAYS);
    assertThat(resultMap.keySet(), hasItem(localDateTimeToString(startOfToday)));
    AggregationResultDto calculatedResult = resultMap.get(localDateTimeToString(startOfToday));
    assertThat(calculatedResult, is(notNullValue()));
    assertThat(calculatedResult, is(calculateExpectedValueGivenDurations(1000L)));
  }

  @Test
  public void simpleReportEvaluationById() throws
                                           Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 1L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .setDateInterval(GroupByDateUnit.DAY)
      .build();

    String reportId = createAndStoreDefaultReportDefinition(reportData);

    // when
    ProcessDurationReportMapResultDto resultDto = evaluateReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = resultDto.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.START_DATE));
    StartDateGroupByDto startDateGroupByDto = (StartDateGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(startDateGroupByDto.getValue().getUnit(), is(GroupByDateUnit.DAY));
    assertThat(resultDto.getResult(), is(notNullValue()));
    assertThat(resultDto.getResult().size(), is(1));
    Map<String, AggregationResultDto> resultMap = resultDto.getResult();
    ZonedDateTime startOfToday = truncateToStartOfUnit(startDate, ChronoUnit.DAYS);
    assertThat(resultMap.keySet(), hasItem(localDateTimeToString(startOfToday)));
    AggregationResultDto calculatedResult = resultMap.get(localDateTimeToString(startOfToday));
    assertThat(calculatedResult, is(notNullValue()));
    assertThat(calculatedResult, is(calculateExpectedValueGivenDurations(1000L)));
  }

  @Test
  public void processInstancesStartedAtSameIntervalAreGroupedTogether() throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 1L);
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 9L);
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 2L);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), startDate, -1L, 1L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setDateInterval(GroupByDateUnit.DAY)
      .build();

    ProcessDurationReportMapResultDto resultDto = evaluateReport(reportData);

    // then
    Map<String, AggregationResultDto> resultMap = resultDto.getResult();
    ZonedDateTime startOfToday = truncateToStartOfUnit(startDate, ChronoUnit.DAYS);
    String expectedStringToday = localDateTimeToString(startOfToday);
    assertThat(resultMap.keySet(), hasItem(expectedStringToday));
    assertThat(resultMap.get(expectedStringToday), is(calculateExpectedValueGivenDurations(1000L, 9000L, 2000L)));
    String expectedStringYesterday = localDateTimeToString(startOfToday.minusDays(1));
    assertThat(resultMap.keySet(), hasItem(expectedStringYesterday));
    assertThat(resultMap.get(expectedStringYesterday), is(calculateExpectedValueGivenDurations(1000L)));
  }

  @Test
  public void resultIsSortedInDescendingOrder() throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 1L);
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, -2L, 3L);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), startDate, -1L, 1L);


    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .build();
    ProcessDurationReportMapResultDto result = evaluateReport(reportData);

    // then
    Map<String, AggregationResultDto> resultMap = result.getResult();
    assertThat(resultMap.size(), is(3));
    assertThat(new ArrayList<>(resultMap.keySet()), isInDescendingOrdering());
  }

  private Matcher<? super List<String>> isInDescendingOrdering() {
    return new TypeSafeMatcher<List<String>>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("The given list should be sorted in descending order!");
      }

      @Override
      protected boolean matchesSafely(List<String> item) {
        for (int i = (item.size() - 1); i > 0; i--) {
          if (item.get(i).compareTo(item.get(i - 1)) > 0) {
            return false;
          }
        }
        return true;
      }
    };
  }

  private void adjustProcessInstanceDates(String processInstanceId,
                                          OffsetDateTime startDate,
                                          long daysToShift,
                                          long durationInSec) throws SQLException {
    OffsetDateTime shiftedStartDate = startDate.plusDays(daysToShift);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceId, shiftedStartDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceId, shiftedStartDate.plusSeconds(durationInSec));
  }

  @Test
  public void emptyIntervalBetweenTwoProcessInstances() throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 1L);
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 9L);
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 2L);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), startDate, -2L, 1L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setProcessDefinitionKey(processDefinitionKey)
      .setDateInterval(GroupByDateUnit.DAY)
      .build();
    ProcessDurationReportMapResultDto result = evaluateReport(reportData);

    // then
    Map<String, AggregationResultDto> resultMap = result.getResult();
    assertThat(resultMap.size(), is(3));

    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);

    String expectedStringToday = localDateTimeToString(startOfToday);
    assertThat(resultMap.containsKey(expectedStringToday), is(true));
    assertThat(resultMap.get(expectedStringToday), is(calculateExpectedValueGivenDurations(1000L, 9000L, 2000L)));
    String expectedStringYesterday = localDateTimeToString(startOfToday.minusDays(1));
    assertThat(resultMap.keySet(), hasItem(expectedStringYesterday));
    assertThat(resultMap.get(expectedStringYesterday), is(calculateExpectedValueGivenDurations(0L)));
    String expectedStringDayBeforeYesterday = localDateTimeToString(startOfToday.minusDays(2));
    assertThat(resultMap.keySet(), hasItem(expectedStringDayBeforeYesterday));
    assertThat(resultMap.get(expectedStringDayBeforeYesterday), is(calculateExpectedValueGivenDurations(1000L)));
  }

  @Test
  public void automaticIntervalSelectionWorks() throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 1L);
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 9L);
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 2L);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), startDate, -2L, 1L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setProcessDefinitionKey(processDefinitionKey)
      .setDateInterval(GroupByDateUnit.AUTOMATIC)
      .build();
    ProcessDurationReportMapResultDto result = evaluateReport(reportData);

    // then
    Map<String, AggregationResultDto> resultMap = result.getResult();
    assertThat(resultMap.size(), is(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION));
    ArrayList<AggregationResultDto> resultValues = new ArrayList<>(resultMap.values());
    assertThat(resultValues.get(0), is(calculateExpectedValueGivenDurations(1000L, 9000L, 2000L)));
    assertThat(resultValues.stream().filter(v -> v.getAvg() > 0L).count(), is(2L));
    assertThat(resultValues.get(resultMap.size() - 1), is(calculateExpectedValueGivenDurations(1000L)));
  }

  @Test
  public void groupedByHour() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(5);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.HOURS);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.HOUR)
      .setProcessDefinitionKey(dto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(dto.getProcessDefinitionVersion())
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, AggregationResultDto> resultMap = result.getResult();
    assertDateResultMap(resultMap, 5, now, ChronoUnit.HOURS);
  }

  private void assertDateResultMap(Map<String, AggregationResultDto> resultMap, int size, OffsetDateTime now,
                                   ChronoUnit unit) {
    assertThat(resultMap.size(), is(size));
    final ZonedDateTime finalStartOfUnit = truncateToStartOfUnit(now, unit);
    IntStream.range(0, size)
      .forEach(i -> {
        String expectedDateString = localDateTimeToString(finalStartOfUnit.minus(i, unit));
        assertThat(resultMap.keySet(), hasItem(expectedDateString));
        assertThat(resultMap.get(expectedDateString), is(calculateExpectedValueGivenDurations(1000L)));
      });
  }

  private void updateProcessInstancesDates(List<ProcessInstanceEngineDto> procInsts,
                                           OffsetDateTime now,
                                           ChronoUnit unit) throws SQLException {
    Map<String, OffsetDateTime> idToNewStartDate = new HashMap<>();
    Map<String, OffsetDateTime> idToNewEndDate = new HashMap<>();
    IntStream.range(0, procInsts.size())
      .forEach(i -> {
        String id = procInsts.get(i).getId();
        OffsetDateTime newStartDate = now.minus(i, unit);
        idToNewStartDate.put(id, newStartDate);
        idToNewEndDate.put(id, newStartDate.plusSeconds(1L));
      });
    engineDatabaseRule.updateProcessInstanceStartDates(idToNewStartDate);
    engineDatabaseRule.updateProcessInstanceEndDates(idToNewEndDate);
  }

  @Test
  public void groupedByDay() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.DAYS);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto processInstanceEngineDto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .setProcessDefinitionVersion(processInstanceEngineDto.getProcessDefinitionVersion())
      .setProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
      .setDateInterval(GroupByDateUnit.DAY)
      .build();
    ProcessDurationReportMapResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, AggregationResultDto> resultMap = result.getResult();
    assertDateResultMap(resultMap, 8, now, ChronoUnit.DAYS);
  }

  @Test
  public void groupedByWeek() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.WEEKS);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setProcessDefinitionKey(dto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(dto.getProcessDefinitionVersion())
      .setDateInterval(GroupByDateUnit.WEEK)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, AggregationResultDto> resultMap = result.getResult();
    assertDateResultMap(resultMap, 8, now, ChronoUnit.WEEKS);
  }

  @Test
  public void groupedByMonth() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.MONTHS);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.MONTH)
      .setProcessDefinitionKey(dto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(dto.getProcessDefinitionVersion())
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, AggregationResultDto> resultMap = result.getResult();
    assertDateResultMap(resultMap, 8, now, ChronoUnit.MONTHS);
  }

  @Test
  public void groupedByYear() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.YEARS);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .setProcessDefinitionVersion(dto.getProcessDefinitionVersion())
      .setProcessDefinitionKey(dto.getProcessDefinitionKey())
      .setDateInterval(GroupByDateUnit.YEAR)
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, AggregationResultDto> resultMap = result.getResult();
    assertDateResultMap(resultMap, 8, now, ChronoUnit.YEARS);
  }

  @Test
  public void reportAcrossAllVersions() throws Exception {
    //given
    OffsetDateTime startDate = OffsetDateTime.now().minusDays(2);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 1L);
    processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 2L);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData);

    // then
    Map<String, AggregationResultDto> resultMap = result.getResult();
    ZonedDateTime startOfToday = truncateToStartOfUnit(startDate, ChronoUnit.DAYS);
    String expectedStartDateString = localDateTimeToString(startOfToday);
    assertThat(resultMap.size(), is(1));
    assertThat(resultMap.containsKey(expectedStartDateString), is(true));
    assertThat(resultMap.get(expectedStartDateString), is(calculateExpectedValueGivenDurations(1000L, 2000L)));
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now().minusDays(2);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 1L);
    deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setDateInterval(GroupByDateUnit.DAY)
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData);

    // then
    Map<String, AggregationResultDto> resultMap = result.getResult();
    ZonedDateTime startOfToday = truncateToStartOfUnit(startDate, ChronoUnit.DAYS);
    String expectedStartDateString = localDateTimeToString(startOfToday);
    assertThat(resultMap.size(), is(1));
    assertThat(resultMap.keySet(), hasItem(expectedStartDateString));
    assertThat(resultMap.get(expectedStartDateString), is(calculateExpectedValueGivenDurations(1000L)));
  }

  @Test
  public void filterInReportWorks() throws Exception {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcessWithVariables(variables);
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 1L);
    String processDefinitionId = processInstanceDto.getDefinitionId();
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .setFilter(createVariableFilter("true"))
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));

    // when
    reportData = ProcessReportDataBuilder
      .createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .setFilter(createVariableFilter("false"))
      .build();

    result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(0));
  }

  private List<ProcessFilterDto> createVariableFilter(String value) {
    VariableFilterDto variableFilterDto = createBooleanVariableFilter("var", value);
    return Collections.singletonList(variableFilterDto);
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ProcessReportDataDto dataDto = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .setProcessDefinitionVersion("1")
      .setDateInterval(GroupByDateUnit.DAY)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .build();

    dataDto.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  @Test
  public void optimizeExceptionOnGroupByValueIsNull() {
    // given
    ProcessReportDataDto dataDto = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .setProcessDefinitionVersion("1")
      .setDateInterval(GroupByDateUnit.DAY)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .build();

    StartDateGroupByDto groupByDto = (StartDateGroupByDto) dataDto.getGroupBy();
    groupByDto.getValue().setUnit(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnGroupByUnitIsNull() {
    // given
    ProcessReportDataDto dataDto = ProcessReportDataBuilder.createReportData()
      .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .setProcessDefinitionVersion("1")
      .setDateInterval(GroupByDateUnit.DAY)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .build();
    StartDateGroupByDto groupByStartDate = (StartDateGroupByDto) dataDto.getGroupBy();
    groupByStartDate.setValue(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    return deployAndStartSimpleProcesses(1).get(0);
  }

  private List<ProcessInstanceEngineDto> deployAndStartSimpleProcesses(int number) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    return IntStream.range(0, number)
      .mapToObj(i -> {
        ProcessInstanceEngineDto processInstanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
        processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
        processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
        return processInstanceEngineDto;
      })
      .collect(Collectors.toList());
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(processModel);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask(ProcessInstanceDurationByStartDateReportEvaluationIT.TEST_ACTIVITY)
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateReportRequest(id, updatedReport)
      .execute();

    assertThat(response.getStatus(), is(204));
  }

  private String createNewReport() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private ProcessDurationReportMapResultDto evaluateReportById(String reportId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId)
      .execute(ProcessDurationReportMapResultDto.class, 200);
  }

  private String createAndStoreDefaultReportDefinition(ProcessReportDataDto reportData) {

    String id = createNewReport();

    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportData);
    report.setId(id);
    report.setLastModifier("something");
    report.setName("something");
    report.setCreated(OffsetDateTime.now());
    report.setLastModified(OffsetDateTime.now());
    report.setOwner("something");
    updateReport(id, report);
    return id;
  }

  private String localDateTimeToString(ZonedDateTime time) {
    return embeddedOptimizeRule.getDateTimeFormatter().format(time);
  }


  private ProcessDurationReportMapResultDto evaluateReport(ProcessReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(ProcessDurationReportMapResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

  private AggregationResultDto calculateExpectedValueGivenDurations(final Long... setDuration) {
    final DescriptiveStatistics statistics = new DescriptiveStatistics();
    Stream.of(setDuration).map(Long::doubleValue).forEach(statistics::addValue);

    return new AggregationResultDto(
      Math.round(statistics.getMin()),
      Math.round(statistics.getMax()),
      Math.round(statistics.getMean()),
      Math.round(statistics.getPercentile(50.0D))
    );
  }

}
