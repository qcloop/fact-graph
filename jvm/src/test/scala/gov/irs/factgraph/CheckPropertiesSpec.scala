package gov.irs.factgraph

import dev.hegel.{ Generators, Hegel, Settings, TestCase }
import gov.irs.factgraph.definitions.{ CheckConfig, FactDictionaryConfigElement }
import gov.irs.factgraph.definitions.fact.{ FactConfigElement, LimitConfig, LimitLevel }
import gov.irs.factgraph.exceptions.FactGraphValidationException
import gov.irs.factgraph.monads.Result
import gov.irs.factgraph.persisters.InMemoryPersister
import org.scalatest.funspec.AnyFunSpec
import scala.jdk.CollectionConverters.*
import scala.xml.{ Null, UnprefixedAttribute }

/** Stateful and parser properties for the public check/limit APIs; fixed truth tables also run on Scala.js. */
class CheckPropertiesSpec extends AnyFunSpec:
  private def property(name: String)(check: TestCase => Unit): Unit =
    it(name) {
      Hegel.test(tc => check(tc), new Settings().testCases(3000).name(s"Checks:$name"))
    }

  private val xml = """<FactDictionaryModule><Facts>
    <Fact path="/employee"><Writable><Boolean/></Writable><Placeholder><False/></Placeholder></Fact>
    <Fact path="/trained"><Writable><Boolean/></Writable><Placeholder><True/></Placeholder></Fact>
    </Facts><Checks><Check id="training" path="/trained" appliesWhen="/employee"/></Checks></FactDictionaryModule>"""
  private val dictionary = FactDictionary.importFromXml(xml)
  private val limits = FactDictionary.importFromXml("""<FactDictionaryModule><Facts>
    <Fact path="/required"><Writable><Int/><Limit type="Min"><Int>0</Int></Limit></Writable></Fact>
    <Fact path="/hours"><Writable><Int/><Limit type="Max" level="Warn"><Int>80</Int></Limit></Writable></Fact>
    </Facts></FactDictionaryModule>""")

  private val guarded = FactDictionary.importFromXml("""<FactDictionaryModule><Facts>
    <Fact path="/employee"><Writable><Boolean/></Writable><Placeholder><True/></Placeholder></Fact>
    <Fact path="/unsafe"><Derived><Equal><Left><Divide>
      <Dividend><Dollar>1</Dollar></Dividend><Divisors><Dollar>0</Dollar></Divisors>
      </Divide></Left><Right><Dollar>1</Dollar></Right></Equal></Derived></Fact>
    </Facts><Checks><Check id="guard" path="/unsafe" appliesWhen="/employee"/></Checks></FactDictionaryModule>""")

  property("inapplicable and provisional applicability never evaluate the assertion") { tc =>
    val supplied = tc.draw(Generators.integers().min(0).max(1), "supplied").intValue == 1
    val graph = Graph(guarded)
    if (supplied) graph.set("/employee", false)
    assert(graph.check().head.status == (if (supplied) CheckStatus.NotApplicable else CheckStatus.Undetermined))
    graph.set("/employee", true)
    intercept[ArithmeticException](graph.check())
  }

  property("missing dependencies are relevant sorted and unique while decisive Booleans remain complete") { tc =>
    val index = tc.draw(Generators.integers().min(0).max(2), "decisivePosition").intValue
    val missing = Seq("<Dependency path='/z'/>", "<Dependency path='/a'/>")
    def operands(constant: String): String = missing.patch(index, Seq(constant), 0).mkString
    val dict = FactDictionary.importFromXml(s"""<FactDictionaryModule><Facts>
      <Fact path="/a"><Writable><Boolean/></Writable></Fact>
      <Fact path="/z"><Writable><Boolean/></Writable></Fact>
      <Fact path="/pending"><Derived><All>${missing.mkString}${missing.head}</All></Derived></Fact>
      <Fact path="/false"><Derived><All>${operands("<False/>")}</All></Derived></Fact>
      <Fact path="/true"><Derived><Any>${operands("<True/>")}</Any></Derived></Fact>
      </Facts><Checks><Check id="pending" path="/pending"/><Check id="false" path="/false"/>
      <Check id="true" path="/true"/></Checks></FactDictionaryModule>""")
    val results = Graph(dict).check()
    assert(results.map(_.status) == Seq(CheckStatus.Undetermined, CheckStatus.Fail, CheckStatus.Pass))
    assert(results.map(_.missingFacts) == Seq(Seq("/a", "/z"), Seq.empty, Seq.empty))
  }

  property("escaped Unicode metadata order and XML node versus string loading agree") { tc =>
    val alphabet = Vector("a", "&", "<", ">", "\"", "'", "λ", "中", "😀", "\n", " ")
    val text = tc
      .draw(Generators.lists(Generators.integers()), "metadata")
      .asScala
      .take(64)
      .map(n => alphabet(Math.floorMod(n.intValue, alphabet.size)))
      .mkString
    val module = <FactDictionaryModule><Facts><Fact path="/ok"><Derived><True/></Derived></Fact></Facts>
      <Checks><Check id="z-first" path="/ok"><Name>{text}</Name><Message>{text}</Message><Source>{text}</Source></Check>
      <Check id="a-second" path="/ok" level="Warn"/></Checks></FactDictionaryModule>
    val stringResults = Graph(FactDictionary.importFromXml(module.toString)).check()
    assert(stringResults == Graph(FactDictionary.fromXml(module)).check())
    assert(stringResults.map(_.id) == Seq("z-first", "a-second"))
    assert(stringResults.head.name == (if (text.trim.isEmpty) "z-first" else text.trim))
    assert(stringResults.head.message == text.trim)
    assert(stringResults.head.source == Option(text.trim).filter(_.nonEmpty))
    assert(stringResults.map(_.level) == Seq(LimitLevel.Error, LimitLevel.Warn))
  }

  property("updates and deletes agree with fresh inputs and checks never write state") { tc =>
    val graph = Graph(dictionary)
    var inputs = Map.empty[String, Boolean]
    for (raw <- tc.draw(Generators.lists(Generators.integers()), "actions").asScala.take(32)) {
      val action = Math.floorMod(raw.intValue, 6)
      val path = if (action < 3) "/employee" else "/trained"
      if (action % 3 == 0) {
        graph.delete(path)
        inputs -= path
      } else {
        val value = action % 3 == 2
        graph.set(path, value)
        inputs += path -> value
      }
      val fresh = Graph(dictionary)
      inputs.foreach((path, value) => fresh.set(path, value))
      val before = graph.persister.toJson()
      val result = graph.check().head
      assert(graph.check() == fresh.check())
      assert(graph.persister.toJson() == before)
      val expected = (inputs.get("/employee"), inputs.get("/trained")) match
        case (Some(false), _)          => CheckStatus.NotApplicable
        case (Some(true), Some(true))  => CheckStatus.Pass
        case (Some(true), Some(false)) => CheckStatus.Fail
        case _                         => CheckStatus.Undetermined
      assert(result.status == expected)
      assert(result.missingFacts == (expected match
        case CheckStatus.Undetermined => Seq(if (inputs.contains("/employee")) "/trained" else "/employee")
        case _                        => Seq.empty))
    }
  }

  property("JSON restore and XML versus configuration loading preserve check results") { tc =>
    val employee = tc.draw(Generators.integers().min(0).max(2), "employee").intValue
    val trained = tc.draw(Generators.integers().min(0).max(2), "trained").intValue
    val facts = Seq(
      FactConfigElement.fromXml(
        <Fact path="/employee"><Writable><Boolean/></Writable><Placeholder><False/></Placeholder></Fact>,
      ),
      FactConfigElement.fromXml(
        <Fact path="/trained"><Writable><Boolean/></Writable><Placeholder><True/></Placeholder></Fact>,
      ),
    )
    val fromConfig = FactDictionary.fromConfig(
      FactDictionaryConfigElement(Meta("1.0"), facts)
        .withChecks(Seq(CheckConfig("training", "/trained", Some("/employee")))),
    )
    val graph = Graph(dictionary)
    val sibling = Graph(fromConfig)
    for ((path, state) <- Seq("/employee" -> employee, "/trained" -> trained) if state > 0) {
      graph.set(path, state == 2)
      sibling.set(path, state == 2)
    }
    val before = graph.persister.toJson()
    assert(Graph(dictionary, InMemoryPersister(before)).check() == graph.check())
    assert(sibling.check() == graph.check())
  }

  property("an Error rejection cannot leak into a later warning-only successful save") { tc =>
    val invalid = tc.draw(Generators.integers().max(-1), "invalid").intValue
    val warned = tc.draw(Generators.integers().min(81), "warned").intValue
    val graph = Graph(limits)
    graph.set("/required", 0)
    val rejected = graph.set("/required", invalid)
    assert(!rejected._1)
    assert(rejected._2.map(_.LimitLevel) == Seq(LimitLevel.Error))
    assert(graph.get("/required") == Result.Complete(0))
    val accepted = graph.set("/hours", warned)
    assert(accepted._1)
    assert(accepted._2.map(_.LimitLevel) == Seq(LimitLevel.Warn))
    assert(graph.get("/required") == Result.Complete(0))
    val restored = Graph(limits, InMemoryPersister(graph.persister.toJson()))
    assert(restored.get("/required") == Result.Complete(0))
    assert(restored.get("/hours") == Result.Complete(warned))
  }

  property("severity parsers accept only documented spellings") { tc =>
    val value = tc.draw(Generators.text(), "level")
    val attribute = new UnprefixedAttribute("level", value, Null)
    val limit = <Limit type="Max"><Int>10</Int></Limit> % attribute
    val check = <Check id="test" path="/trained"/> % attribute
    if (value.isEmpty || value.equalsIgnoreCase("Error") || value.equalsIgnoreCase("Warn")) {
      val expected = if (value.equalsIgnoreCase("Warn")) LimitLevel.Warn else LimitLevel.Error
      assert(LimitConfig.fromXml(limit).level == expected)
      assert(CheckConfig.fromXml(check).level == expected)
    } else {
      intercept[FactGraphValidationException](LimitConfig.fromXml(limit))
      intercept[FactGraphValidationException](CheckConfig.fromXml(check))
    }
  }

  property("dictionary cleanup cannot resurrect removals after a retained value fails a limit") { tc =>
    val suffix = tc.draw(Generators.integers(), "suffix").toString.replace('-', 'n')
    val known = s"/known$suffix"
    val unknown = s"/removed$suffix"
    val invalid = tc.draw(Generators.integers().max(-1), "invalid").intValue
    val dict = FactDictionary.importFromXml(s"""<FactDictionaryModule><Facts>
      <Fact path="$known"><Writable><Int/><Limit type="Min"><Int>0</Int></Limit></Writable></Fact>
      </Facts></FactDictionaryModule>""")
    val persister = InMemoryPersister(known -> invalid, unknown -> invalid)
    val graph = Graph(dict, persister)
    val issues = graph.checkPersister()
    assert(issues.map(_.path) == Seq(unknown))
    assert(!ujson.read(persister.toJson()).obj.contains(unknown))
    graph.save()
    assert(!ujson.read(persister.toJson()).obj.contains(unknown))
    assert(graph.checkPersister().isEmpty)
  }

  property("check paths reject malformed and undeclared inputs at the dictionary boundary") { tc =>
    val path = tc.draw(Generators.text(), "path")
    val facts = Seq(FactConfigElement.fromXml(<Fact path="/trained"><Writable><Boolean/></Writable></Fact>))
    def load(): FactDictionary = FactDictionary.fromConfig(
      FactDictionaryConfigElement(Meta("1.0"), facts)
        .withChecks(Seq(CheckConfig("test", path))),
    )
    if (path == "/trained") assert(Graph(load()).check().head.status == CheckStatus.Undetermined)
    else intercept[FactGraphValidationException](load())
  }
