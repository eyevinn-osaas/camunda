import React from 'react';
import {getFlowNodeNames} from 'services';
import update from 'immutability-helper';
import {withErrorHandling} from 'HOC';

import {getFormatter, processResult as processSingleReportResult} from './service';
import ReportBlankSlate from './ReportBlankSlate';
import {Table, Chart} from './visualizations';

const getComponent = visualization => {
  switch (visualization) {
    case 'table':
      return Table;
    case 'number':
    case 'bar':
    case 'line':
      return Chart;
    default:
      return ReportBlankSlate;
  }
};

export default withErrorHandling(
  class CombinedReportRenderer extends React.Component {
    state = {
      flowNodeNames: {}
    };

    componentDidMount() {
      this.loadAllFlowNodeNames();
    }

    componentDidUpdate(prevProps) {
      if (prevProps.report.result !== this.props.report.result) {
        this.loadAllFlowNodeNames();
      }
    }

    loadAllFlowNodeNames = () => {
      const {result} = this.props.report;
      if (result && typeof result === 'object') {
        Object.values(result).forEach(({data: {processDefinitionKey, processDefinitionVersion}}) =>
          this.loadFlowNodeNames(processDefinitionKey, processDefinitionVersion)
        );
      }
    };

    loadFlowNodeNames = async (key, version) => {
      this.props.mightFail(getFlowNodeNames(key, version), flowNodeNames =>
        this.setState(
          update(this.state, {
            flowNodeNames: {$merge: flowNodeNames}
          })
        )
      );
    };

    render() {
      const {result} = this.props.report;
      if (result && typeof result === 'object' && Object.keys(result).length) {
        const {view, visualization} = Object.values(result)[0].data;
        const Component = getComponent(visualization);

        const processedReport = {
          ...this.props.report,
          result: processResult(this.props.report.result)
        };

        return (
          <div className="component">
            <Component
              {...this.props}
              report={processedReport}
              flowNodeNames={this.state.flowNodeNames}
              formatter={getFormatter(view.property)}
            />
          </div>
        );
      }

      return (
        <ReportBlankSlate
          isCombined
          errorMessage={'To display a report, please select one or more reports from the list.'}
        />
      );
    }
  }
);

function processResult(reports) {
  return Object.entries(reports).reduce((result, [reportId, report]) => {
    result[reportId] = {...report, result: processSingleReportResult(report)};
    return result;
  }, {});
}
