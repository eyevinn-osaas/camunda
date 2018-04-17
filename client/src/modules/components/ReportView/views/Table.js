import React from 'react';
import ReportBlankSlate from '../ReportBlankSlate';

import {Table as TableRenderer} from 'components';

export default function Table({data, formatter, labels, errorMessage, disableReportScrolling}) {
  if (!data || typeof data !== 'object') {
    return <ReportBlankSlate message={errorMessage} />;
  }

  return (
    <TableRenderer
      disableReportScrolling={disableReportScrolling}
      {...formatData(data, formatter, labels)}
    />
  );
}

function formatData(data, formatter, labels) {
  if (data.length) {
    // raw data
    const processInstanceProps = Object.keys(data[0]).filter(entry => entry !== 'variables');
    const variableNames = Object.keys(data[0].variables);

    const body = data.map(instance => {
      let row = processInstanceProps.map(entry => instance[entry]);
      const variableValues = variableNames.map(entry => instance.variables[entry]);
      row.push(...variableValues);
      row = row.map(entry => (entry === null ? '' : entry.toString()));
      return row;
    });

    const head = processInstanceProps;
    head.push({label: 'Variables', columns: variableNames});

    return {head, body};
  } else {
    // normal two-dimensional data
    const body = Object.keys(data).map(key => [key, formatter(data[key])]);
    return {head: labels, body};
  }
}
