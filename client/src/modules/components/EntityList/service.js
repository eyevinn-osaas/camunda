import {get, post, del, put} from 'request';

export async function load(api, numResults, sortBy) {
  const url = `/api/${api}`;
  const apis = api + 's';

  const params = {};
  if (numResults) {
    params['numResults'] = numResults;
  }

  if (sortBy) {
    params['orderBy'] = sortBy;
  }

  const response = await get(url, params);
  const json = await response.json();

  const idList = json.map(entry => entry.id);

  const shareStatusResponse = await post(`/api/share/status`, {[apis]: idList});
  const shareStatus = await shareStatusResponse.json();

  if (!shareStatus[apis]) return json;

  return json.map(entry => ({
    ...entry,
    shared: shareStatus[apis][entry.id]
  }));
}

export async function create(api, data) {
  const subUrl = api === 'report' ? `/${data.reportType}` : '';

  const response = await post(`/api/${api}${subUrl}`, data);

  const json = await response.json();

  return json.id;
}

export async function duplicate(api, copyData) {
  const subUrl = copyData.reportType ? `/${copyData.reportType}` : '';

  const createResponse = await post(`/api/${api}${subUrl}`);

  const json = await createResponse.json();

  return await update(api, json.id, copyData);
}

export async function remove(id, api) {
  return await del(`/api/${api}/${id}`);
}

export async function update(api, id, data) {
  return await put(`/api/${api}/${id}`, data);
}
