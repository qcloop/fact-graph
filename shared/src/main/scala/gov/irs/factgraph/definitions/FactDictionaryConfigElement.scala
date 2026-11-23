package gov.irs.factgraph.definitions

import gov.irs.factgraph.definitions.fact.FactConfigTrait
import gov.irs.factgraph.definitions.meta.MetaConfigTrait

case class FactDictionaryConfigElement(
    meta: MetaConfigTrait,
    facts: Seq[FactConfigTrait],
) extends FactDictionaryConfigTrait:
  /** Add checks while retaining the existing two-field constructor and extractor. */
  def withChecks(configs: Seq[CheckConfig]): FactDictionaryConfigTrait =
    val original = this
    new FactDictionaryConfigTrait:
      override val meta = original.meta
      override val facts = original.facts
      override val checks = configs
