import createTargetLineData from './createTargetLineData';

it('should create two datasets for line chart with target values', () => {
  const result = {foo: 123, bar: 5, dar: 5};
  const targetValue = {target: 10};

  const chartData = createTargetLineData({
    report: {
      result,
      data: {
        configuration: {color: ['blue']},
        visualization: 'line',
        groupBy: {
          type: '',
          value: ''
        }
      },
      combined: false
    },
    theme: 'light',
    targetValue
  });
  expect(chartData.datasets).toHaveLength(2);
});

it('should create two datasets for each report in combined line charts with target values', () => {
  const result = {foo: 123, bar: 5, dar: 5};
  const name = 'test1';
  const targetValue = {target: 10};
  const chartData = createTargetLineData({
    report: {
      result: {reportA: {name, result}, reportB: {name, result}},
      data: {
        reportIds: ['reportA', 'reportB'],
        configuration: {reportColors: ['blue', 'yellow']},
        visualization: 'line',
        groupBy: {
          type: '',
          value: ''
        }
      },
      combined: true
    },
    targetValue,
    theme: 'light'
  });

  expect(chartData.datasets).toHaveLength(4);
});
