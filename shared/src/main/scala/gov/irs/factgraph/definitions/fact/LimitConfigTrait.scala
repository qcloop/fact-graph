package gov.irs.factgraph.definitions.fact

import gov.irs.factgraph.exceptions.FactGraphValidationException

enum LimitLevel:
  case Warn, Error

object LimitLevel:
  def fromXml(value: String): LimitLevel = value match
    case ""                                       => LimitLevel.Error
    case value if value.equalsIgnoreCase("Error") => LimitLevel.Error
    case value if value.equalsIgnoreCase("Warn")  => LimitLevel.Warn
    case other => throw FactGraphValidationException(s"Invalid limit level '$other'; expected Error or Warn")

trait LimitConfigTrait {
  def operation: String
  def level: LimitLevel
  def node: CompNodeConfigTrait
}
