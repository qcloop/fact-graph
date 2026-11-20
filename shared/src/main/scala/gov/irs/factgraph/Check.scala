package gov.irs.factgraph

import gov.irs.factgraph.compnodes.BooleanNode
import gov.irs.factgraph.definitions.fact.LimitLevel
import gov.irs.factgraph.definitions.CheckConfig
import gov.irs.factgraph.exceptions.FactGraphValidationException
import gov.irs.factgraph.monads.Result
import scala.annotation.tailrec
import scala.util.control.NonFatal

enum CheckStatus:
  case Pass, Fail, NotApplicable, Undetermined

  def code: String = this match
    case Pass          => "pass"
    case Fail          => "fail"
    case NotApplicable => "notApplicable"
    case Undetermined  => "undetermined"

final case class CheckResult(
    id: String,
    name: String,
    message: String,
    source: Option[String],
    level: LimitLevel,
    status: CheckStatus,
    missingFacts: Seq[String],
)

/** A compiled check refers to declared facts, and uses their existing completeness and explanation semantics. */
final class Check private (val config: CheckConfig):
  private[factgraph] def evaluate(graph: Graph): CheckResult =
    val applicability = config.appliesWhen.map(graph.get).getOrElse(Result.Complete(true))
    val (status, missingFacts) = applicability match
      case Result.Complete(false) => (CheckStatus.NotApplicable, Seq.empty)
      case Result.Complete(true)  =>
        graph.get(config.path) match
          case Result.Complete(true)  => (CheckStatus.Pass, Seq.empty)
          case Result.Complete(false) => (CheckStatus.Fail, Seq.empty)
          case _                      => (CheckStatus.Undetermined, missing(graph, config.path))
      case _ => (CheckStatus.Undetermined, missing(graph, config.appliesWhen.get))

    CheckResult(
      config.id,
      if (config.name.nonEmpty) config.name else config.id,
      config.message,
      config.source,
      config.level,
      status,
      missingFacts,
    )

  private def missing(graph: Graph, path: String): Seq[String] =
    // Traverse the explanation directly, avoiding the combinatorial expansion of Explanation.solves.
    @tailrec
    def collect(pending: List[Explanation], paths: Set[String]): Set[String] = pending match
      case Explanation.Writable(false, path) :: tail => collect(tail, paths + path.toString)
      case head :: tail                              => collect(head.children.flatten ::: tail, paths)
      case Nil                                       => paths
    collect(List(graph.explain(path)), Set.empty).toSeq.sorted

object Check:
  private[factgraph] def fromConfig(config: CheckConfig, dictionary: FactDictionary): Check =
    if (config.id.trim.isEmpty)
      throw FactGraphValidationException("Check requires a non-empty id")

    for (value <- Seq(config.path) ++ config.appliesWhen) {
      // Check empty segments before PathItem.charAt: unchecked indexing is undefined behavior on Scala.js.
      if (!value.startsWith("/") || value.length <= 1 || value.endsWith("/") || value.contains("//"))
        throw FactGraphValidationException(s"Check ${config.id} has an invalid fact path: '$value'")
      val path =
        try Path(value)
        catch
          case NonFatal(_) =>
            throw FactGraphValidationException(s"Check ${config.id} has an invalid fact path: '$value'")
      if (!path.isAbstract || path.isWildcard || path.toString != value)
        throw FactGraphValidationException(s"Check ${config.id} requires an absolute scalar fact path: '$value'")
      dictionary(path) match
        case Some(definition) if definition.value.isInstanceOf[BooleanNode] && definition.size == Factual.Size.Single =>
        case _                                                                                                        =>
          throw FactGraphValidationException(s"Check ${config.id} requires a declared scalar Boolean fact: '$value'")
    }
    new Check(
      config.copy(
        id = config.id.trim,
        name = config.name.trim,
        message = config.message.trim,
        source = config.source.map(_.trim).filter(_.nonEmpty),
      ),
    )
