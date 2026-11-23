import { createGraph, setErrorMessage, showMessage, showWarnings, snapshot } from './common.js'

let graph
let loadSequence = 0
const error = document.querySelector('#error')
const warnings = document.querySelector('#warnings')

document.querySelector('#input').addEventListener('change', async event => {
  const file = event.target.files[0]
  if (!file) return
  const sequence = ++loadSequence
  try {
    const xml = await file.text()
    if (sequence !== loadSequence) return
    const candidate = createGraph(xml)
    const options = candidate.paths().map(path => {
      const option = document.createElement('option')
      option.value = path
      return option
    })
    graph = candidate
    document.querySelector('#facts').replaceChildren(...options)
    document.querySelector('#set-fields').disabled = false
    document.querySelector('#get-fields').disabled = false
    document.querySelector('#dictionary-status').textContent = `${file.name}: ${options.length} fact paths loaded.`
    document.querySelector('#fact-result').textContent = 'No fact selected.'
    showMessage(error)
    showWarnings(warnings)
    displayGraph()
  } catch (exception) {
    if (sequence === loadSequence) showMessage(error, exception.message)
  }
})

document.querySelector('#set-fact-form').addEventListener('submit', event => {
  event.preventDefault()
  const fields = event.target.elements
  const path = fields.namedItem('path').value
  try {
    const result = graph.set(path, fields.namedItem('value').value)
    showWarnings(warnings, result.warnings)
    showMessage(error, result.errorType ? setErrorMessage(path, result) : '')
    displayGraph()
  } catch (exception) {
    showMessage(error, exception.message)
  }
})

document.querySelector('#get-fact-form').addEventListener('submit', event => {
  event.preventDefault()
  try {
    const result = graph.get(event.target.elements.namedItem('path').value)
    document.querySelector('#fact-result').textContent = String(result)
    showMessage(error)
  } catch (exception) {
    showMessage(error, exception.message)
  }
})

function displayGraph() {
  document.querySelector('#graph').textContent = snapshot(graph)
  const results = typeof graph.check === 'function' ? graph.check() : []
  document.querySelector('#checks-section').hidden = results.length === 0
  document.querySelector('#checks').textContent = JSON.stringify(results, null, 2)
}
