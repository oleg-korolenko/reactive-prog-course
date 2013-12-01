package suggestions


import language.postfixOps
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Try, Success, Failure}
import rx.lang.scala._
import org.scalatest._
import gui._

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner


@RunWith(classOf[JUnitRunner])
class WikipediaApiTest extends FunSuite {

  object mockApi extends WikipediaApi {
    def wikipediaSuggestion(term: String) = Future {
      if (term.head.isLetter) {
        for (suffix <- List(" (Computer Scientist)", " (Footballer)")) yield term + suffix
      } else {
        List(term)
      }
    }

    def wikipediaPage(term: String) = Future {
      "Title: " + term
    }
  }

  import mockApi._

  test("WikipediaApi should make the stream valid using sanitized") {
    val notvalid = Observable("erik", "erik meijer", "martin")
    val valid = notvalid.sanitized

    var count = 0
    var completed = false

    val sub = valid.subscribe(
      term => {
        assert(term.forall(_ != ' '))
        count += 1
      },
      t => assert(false, s"stream error $t"),
      () => completed = true
    )
    assert(completed && count == 3, "completed: " + completed + ", event count: " + count)
  }

  test("timedOut should collect the correct number of values") {
    val clock = Observable.interval(1 seconds)
    val timedOut = clock.timedOut(3)
    assert(timedOut.toBlockingObservable.toList.length === 2)
  }

  test("my test") {
    val toRecover: Observable[Int] = Observable(1, 2)++Observable(new Exception("oops"))

    val recovered = toRecover.recovered

    var success = 0
    var failures = 0

    val sub = recovered.subscribe(
      succ => {
        println("(S)"+succ)
        success += 1
      },
      err => {
        println("(F)"+err)
        failures += 1
      },
      () => println("End of stream")
    )

    assert(success == 2, "success=" + success)
    assert(failures == 0, "failures=" + failures)
  }


  test("WikipediaApi should correctly use concatRecovered") {
    val requests = Observable(1, 2, 3)
    val remoteComputation = (n: Int) => Observable(0 to n)
    val responses = requests concatRecovered remoteComputation
    val sum = responses.foldLeft(0) {
      (acc, tn) =>
        tn match {
          case Success(n) => acc + n
          case Failure(t) => throw t
        }
    }
    var total = -1
    val sub = sum.subscribe {
      s => total = s
    }
    assert(total == (1 + 1 + 2 + 1 + 2 + 3), s"Sum: $total")
  }

  test("WikipediaApi failing case") {
    val requests = Observable(1, 2, 3, 4, 5)
   // Set(Success(erik (Computer Scientist)), Failure(suggestions.WikipediaApiTest$WhitespaceException))
    val remoteComputation =(num:Int) => if (num != 4) Observable(num) else Observable(new Exception)
    val responses = requests concatRecovered remoteComputation
    val sub = responses.subscribe(
      succ => {
        println("(S)"+succ)

      },
      err => {
        println("(F)"+err)
             },
      () => println("End of stream")
    )

    //assert(total == (1 + 1 + 2 + 1 + 2 + 3), s"Sum: $total")
  }
}