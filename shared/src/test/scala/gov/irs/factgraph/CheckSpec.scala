package gov.irs.factgraph

import gov.irs.factgraph.definitions.{ CheckConfig, FactDictionaryConfigElement }
import gov.irs.factgraph.definitions.fact.{ FactConfigElement, LimitLevel }
import gov.irs.factgraph.exceptions.FactGraphValidationException
import gov.irs.factgraph.monads.Result
import gov.irs.factgraph.persisters.InMemoryPersister
import org.scalatest.funspec.AnyFunSpec

class CheckSpec extends AnyFunSpec:
  private def dictionary(
      applicabilityPlaceholder: Option[Boolean] = None,
      assertionPlaceholder: Option[Boolean] = None,
  ): FactDictionary =
    def placeholder(value: Option[Boolean]): String =
      value.map(v => s"<Placeholder><${if (v) "True" else "False"}/></Placeholder>").getOrElse("")
    FactDictionary.importFromXml(s"""
      <FactDictionaryModule>
        <Facts>
          <Fact path="/employee"><Writable><Boolean/></Writable>${placeholder(applicabilityPlaceholder)}</Fact>
          <Fact path="/trained"><Writable><Boolean/></Writable>${placeholder(assertionPlaceholder)}</Fact>
        </Facts>
        <Checks>
          <Check id="training" path="/trained" appliesWhen="/employee" level="Warn">
            <Name>Current training</Name><Message>Training is required.</Message><Source>Access policy</Source>
          </Check>
        </Checks>
      </FactDictionaryModule>""")

  describe("policy checks") {
    // Each axis includes confirmed false/true, missing, and false/true placeholders.
    val states = Seq(Some(false) -> None, Some(true) -> None, None -> None, None -> Some(false), None -> Some(true))
    for {
      (input, fallback) <- states
      (assertion, assertionFallback) <- states
    }
      it(s"distinguishes applicability $input/$fallback from assertion $assertion/$assertionFallback") {
        val graph = Graph(dictionary(fallback, assertionFallback))
        input.foreach(value => graph.set("/employee", value))
        assertion.foreach(value => graph.set("/trained", value))
        val expected = (input, assertion) match
          case (Some(false), _)          => CheckStatus.NotApplicable
          case (Some(true), Some(true))  => CheckStatus.Pass
          case (Some(true), Some(false)) => CheckStatus.Fail
          case _                         => CheckStatus.Undetermined
        assert(graph.check().head.status == expected)
      }

    it("keeps a confirmed policy violation as complete, valid input") {
      val graph = Graph(dictionary())
      assert(graph.set("/employee", true)._1)
      assert(graph.set("/trained", false)._1)
      assert(graph.get("/trained") == Result.Complete(false))
      val before = graph.persister.toJson()
      val result = graph.check().head
      assert(result.status == CheckStatus.Fail)
      assert(result.name == "Current training")
      assert(result.message == "Training is required.")
      assert(result.source.contains("Access policy"))
      assert(result.level == LimitLevel.Warn)
      assert(result.missingFacts.isEmpty)
      assert(graph.persister.toJson() == before)
    }

    it("reports missing applicability before asking for assertion inputs") {
      val graph = Graph(dictionary())
      assert(graph.check().head.missingFacts == Seq("/employee"))
      graph.set("/employee", true)
      assert(graph.check().head.missingFacts == Seq("/trained"))
      graph.set("/trained", true)
      assert(graph.check().head.status == CheckStatus.Pass)
      graph.delete("/trained")
      assert(graph.check().head.status == CheckStatus.Undetermined)
    }

    it("restores answers without persisting check results") {
      val dict = dictionary()
      val graph = Graph(dict)
      graph.set("/employee", true)
      graph.set("/trained", false)
      val restored = Graph(dict, InMemoryPersister(graph.persister.toJson()))
      assert(restored.check() == graph.check())
      assert(!graph.persister.toJson().contains("training"))
    }

    it("supports configuration entry points and default applicability") {
      val fact = FactConfigElement.fromXml(<Fact path="/ok"><Derived><True/></Derived></Fact>)
      val config = FactDictionaryConfigElement(Meta("1.0"), Seq(fact)).withChecks(Seq(CheckConfig("ok", "/ok")))
      val graph = Graph(FactDictionary.fromConfig(config))
      val result = graph.check().head
      assert(result.status == CheckStatus.Pass)
      assert(result.name == "ok")
      assert(result.message.isEmpty)
      assert(result.source.isEmpty)
      assert(result.level == LimitLevel.Error)
    }

    it("preserves existing config extractors and custom trait implementations") {
      val original = FactDictionaryConfigElement(Meta("1.0"), Seq.empty)
      val FactDictionaryConfigElement(meta, facts) = original
      assert(meta == original.meta && facts == original.facts)
      val legacy = new definitions.FactDictionaryConfigTrait:
        override val meta = Meta("1.0")
        override val facts = Seq.empty
      assert(Graph(FactDictionary.fromConfig(legacy)).check().isEmpty)
      assert(original.checks.isEmpty)
    }

    it("normalizes blank configuration metadata and duplicate IDs consistently with XML") {
      val fact = FactConfigElement.fromXml(<Fact path="/ok"><Derived><True/></Derived></Fact>)
      val check = CheckConfig(" test ", "/ok", name = "  ", message = " message ", source = Some(" "))
      val config = FactDictionaryConfigElement(Meta("1.0"), Seq(fact)).withChecks(Seq(check))
      val result = Graph(FactDictionary.fromConfig(config)).check().head
      assert(result.id == "test" && result.name == "test")
      assert(result.message == "message" && result.source.isEmpty)
      intercept[FactGraphValidationException] {
        FactDictionary.fromConfig(
          FactDictionaryConfigElement(Meta("1.0"), Seq(fact))
            .withChecks(Seq(check, check.copy(id = "test"))),
        )
      }
    }

    it("leaves dictionaries without checks unchanged") {
      assert(
        Graph(FactDictionary.importFromXml("<FactDictionaryModule><Facts/></FactDictionaryModule>")).check().isEmpty,
      )
    }

    for (path <- Seq("", "/unknown", "trained", "/", "/items/*/ok", "/a//b", "/#bad", "/trained/"))
      it(s"rejects an undeclared or non-scalar check path '$path'") {
        intercept[FactGraphValidationException] {
          FactDictionary.importFromXml(s"""<FactDictionaryModule><Facts/>
            <Checks><Check id="bad" path="$path"/></Checks></FactDictionaryModule>""")
        }
      }

    it("rejects non-Boolean assertions and applicability") {
      for ((path, appliesWhen) <- Seq("/count" -> "/ok", "/ok" -> "/count"))
        intercept[FactGraphValidationException] {
          FactDictionary.importFromXml(
            s"""<FactDictionaryModule><Facts>
            <Fact path="/count"><Writable><Int/></Writable></Fact>
            <Fact path="/ok"><Writable><Boolean/></Writable></Fact>
            </Facts><Checks><Check id="bad" path="$path" appliesWhen="$appliesWhen"/></Checks></FactDictionaryModule>""",
          )
        }
    }

    for (
      checks <- Seq(
        "<Check path='/ok'/>",
        "<Check id=' ' path='/ok'/>",
        "<Check id='x' path='/ok'/><Check id='x' path='/ok'/>",
        "<Check id='x' path='/ok' appliesWhen=''/>",
        "<Check id='x' path='/ok' level='warning'/>",
        "<Check id='x' path='/ok' appliesWen='/ok'/>",
        "<Check id='x' path='/ok'><Message>a</Message><Message>b</Message></Check>",
        "<Check id='x' path='/ok'><Message><True/></Message></Check>",
        "<Check id='x' path='/ok'><Assert><True/></Assert></Check>",
        "<Check id='x' path='/ok'>unexpected text</Check>",
        "<Unknown/>",
      )
    )
      it(s"rejects malformed check declarations: $checks") {
        intercept[FactGraphValidationException] {
          FactDictionary.importFromXml(s"""<FactDictionaryModule>
            <Facts><Fact path="/ok"><Derived><True/></Derived></Fact></Facts>
            <Checks>$checks</Checks></FactDictionaryModule>""")
        }
      }

    for (
      fragment <- Seq(
        "<Check id='x' path='/ok'/>",
        "<Checks>unexpected text</Checks>",
        "<Checks level='Warn'/>",
      )
    )
      it(s"rejects misplaced checks or malformed containers: $fragment") {
        intercept[FactGraphValidationException] {
          FactDictionary.importFromXml(s"""<FactDictionaryModule>
            <Facts><Fact path="/ok"><Derived><True/></Derived></Fact></Facts>
            $fragment</FactDictionaryModule>""")
        }
      }
  }
