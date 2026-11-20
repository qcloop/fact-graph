package gov.irs.factgraph.definitions

import gov.irs.factgraph.definitions.fact.FactConfigTrait
import gov.irs.factgraph.definitions.meta.MetaConfigTrait

trait FactDictionaryConfigTrait {
  def facts: Iterable[FactConfigTrait]
  def meta: MetaConfigTrait
  def checks: Iterable[CheckConfig] = Seq.empty

}
