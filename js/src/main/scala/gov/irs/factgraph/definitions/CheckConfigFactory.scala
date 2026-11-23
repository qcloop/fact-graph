package gov.irs.factgraph.definitions

import gov.irs.factgraph.definitions.fact.LimitLevel
import scala.scalajs.js
import scala.scalajs.js.annotation.{ JSExport, JSExportTopLevel }
import scala.scalajs.js.JSConverters.*

@JSExportTopLevel("CheckConfigFactory")
object CheckConfigFactory:
  @JSExport
  def create(
      id: String,
      path: String,
      appliesWhen: js.UndefOr[String] = js.undefined,
      level: String = "Error",
      name: String = "",
      message: String = "",
      source: js.UndefOr[String] = js.undefined,
  ): CheckConfig =
    CheckConfig(
      id.trim,
      path.trim,
      appliesWhen.toOption.map(_.trim),
      LimitLevel.fromXml(level),
      name.trim,
      message.trim,
      source.toOption.map(_.trim).filter(_.nonEmpty),
    )
