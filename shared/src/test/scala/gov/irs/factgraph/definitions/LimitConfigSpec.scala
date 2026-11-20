package gov.irs.factgraph.definitions

import gov.irs.factgraph.{ FactDictionary, Graph }
import gov.irs.factgraph.definitions.fact.{ LimitConfig, LimitLevel }
import gov.irs.factgraph.exceptions.FactGraphValidationException
import gov.irs.factgraph.monads.Result
import org.scalatest.funspec.AnyFunSpec

class LimitConfigSpec extends AnyFunSpec:
  describe("XML limit severity") {
    it("defaults existing declarations to Error") {
      assert(LimitConfig.fromXml(<Limit type="Max"><Int>10</Int></Limit>).level == LimitLevel.Error)
    }

    for (level <- Seq("Error", "Warn", "error", "warn", "ERROR", "WARN", "wArN"))
      it(s"honors an explicit $level level when saving") {
        val dictionary = FactDictionary.importFromXml(s"""<FactDictionaryModule><Facts>
          <Fact path="/hours"><Writable><Int/><Limit type="Max" level="$level"><Int>10</Int></Limit></Writable></Fact>
          </Facts></FactDictionaryModule>""")
        val graph = Graph(dictionary)
        val (saved, issues) = graph.set("/hours", 11)
        assert(saved == level.equalsIgnoreCase("Warn"))
        assert(issues.size == 1)
        assert(issues.head.LimitLevel.toString.equalsIgnoreCase(level))
        assert(graph.get("/hours") == (if (saved) Result.Complete(11) else Result.Incomplete))
      }

    it("rejects misspelled levels") {
      intercept[FactGraphValidationException] {
        LimitConfig.fromXml(<Limit type="Max" level="warning"><Int>10</Int></Limit>)
      }
    }
  }
