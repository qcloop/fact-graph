import { createGraph, setErrorMessage, showMessage, showWarnings, snapshot } from './common.js'

let graph
const form = document.querySelector('#compliance-form')
const fields = form.elements
const error = document.querySelector('#error')
const warnings = document.querySelector('#warnings')
const labels = {
  '/asOf': 'Evaluation date',
  '/isEmployee': 'Employee status',
  '/trainingExpiresOn': 'Training expiration date',
  '/reportedTrainingHours': 'Reported training hours',
}
const statuses = {
  pass: 'Pass',
  fail: 'Fail',
  undetermined: 'Undetermined',
  notApplicable: 'Not applicable',
}
const scenarios = {
  current: { employee: 'true', expires: '2026-12-31' },
  expired: { employee: 'true', expires: '2026-10-31' },
  missing: { employee: 'true', expires: '' },
  exempt: { employee: 'false', expires: '' },
}

form.addEventListener('submit', event => {
  event.preventDefault()
  updateChecklist()
})

for (const button of document.querySelectorAll('[data-scenario]')) {
  button.addEventListener('click', () => {
    form.reset()
    const scenario = scenarios[button.dataset.scenario]
    fields.namedItem('employee').value = scenario.employee
    fields.namedItem('expires-on').value = scenario.expires
    updateChecklist()
  })
}

async function loadExample() {
  try {
    const response = await fetch('./compliance.xml')
    if (!response.ok) throw new Error(`Unable to load example: ${response.status}`)
    graph = createGraph(await response.text())
    if (typeof graph.check !== 'function') {
      throw new Error('Rebuild the library and run demo/copy-fg.sh to enable checks.')
    }
    document.querySelector('#compliance-fields').disabled = false
    document.querySelector('#scenario-fields').disabled = false
    updateChecklist()
  } catch (exception) {
    showMessage(error, exception.message)
    document.querySelector('#completeness').textContent = 'Unavailable'
    document.querySelector('#policy-status').textContent = 'Unavailable'
  }
}

function updateChecklist() {
  const values = [
    ['/asOf', fields.namedItem('as-of').value],
    ['/isEmployee', fields.namedItem('employee').value],
    ['/trainingExpiresOn', fields.namedItem('expires-on').value],
    ['/reportedTrainingHours', fields.namedItem('hours').value],
  ]
  const findings = []
  const errors = []
  try {
    for (const [path, value] of values) {
      if (value === '') {
        graph.delete(path)
      } else {
        const result = graph.set(path, value)
        findings.push(...result.warnings)
        if (result.errorType) errors.push(setErrorMessage(labels[path], result))
      }
    }
    showMessage(error, errors.join(' '))
    showWarnings(warnings, findings, labels)
    displayChecklist()
  } catch (exception) {
    showMessage(error, exception.message)
  }
}

function displayChecklist() {
  const results = graph.check()
  const result = results[0]
  const answered = ['/asOf', '/isEmployee', '/trainingExpiresOn'].filter(path => graph.get(path).complete).length
  document.querySelector('#completeness').textContent = `${answered} of 3`
  const status = document.querySelector('#policy-status')
  status.textContent = statuses[result.status]
  status.dataset.status = result.status
  document.querySelector('#policy-message').textContent = result.message
  document.querySelector('#missing-inputs').textContent = result.missingFacts.length
    ? `Missing information: ${result.missingFacts.map(path => labels[path] || path).join(', ')}.`
    : ''
  document.querySelector('#policy-source').textContent = result.source || ''
  document.querySelector('#checks').textContent = JSON.stringify(results, null, 2)
  document.querySelector('#graph').textContent = snapshot(graph)
}

loadExample()
