import * as fg from './fg.generated.mjs'

export function createGraph(xml) {
  return fg.GraphFactory.apply(fg.FactDictionaryFactory.importFromXml(xml))
}

export function showMessage(element, message = '') {
  element.textContent = message
  element.hidden = !message
}

export function showWarnings(element, warnings = [], labels = {}) {
  showMessage(element, warnings.map(warning =>
    `${labels[warning.factPath] || warning.factPath}: ${warning.limitName} ${warning.limit} (entered ${warning.actual})`,
  ).join('; '))
}

export function setErrorMessage(path, result) {
  const expected = result.expectedValue == null ? '' : ` Expected ${result.expectedValue}.`
  return `Could not save ${path}: ${result.errorName || result.errorType}.${expected}`
}

export function snapshot(graph) {
  return JSON.stringify(JSON.parse(graph.toJSON()), null, 2)
}
