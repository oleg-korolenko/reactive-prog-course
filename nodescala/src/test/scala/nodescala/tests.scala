package nodescala


import scala.language.postfixOps
import scala.util.{Try, Success, Failure}
import scala.collection._
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.async.Async.{async, await}
import org.scalatest._
import NodeScala._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import java.lang

@RunWith(classOf[JUnitRunner])
class NodeScalaSuite extends FunSuite {

  test("always : A Future should always be created") {
    val always = Future.always(517)

    assert(Await.result(always, 0 nanos) == 517)
  }

  test("never : A Future should never be completed") {
    val never = Future.never[Int]

    try {
      Await.result(never, 1 second)
      assert(false)
    } catch {
      case t: TimeoutException => // ok!
    }
  }

  test("all : Should get exception for one of the T") {
    val all = Future.all(List(Future("1"), Future(2 / 0), Future("end")))
    try {
      Await.result(all, 1 second)
      assert(false)
    } catch {
      case t: ArithmeticException => // ok!
    }
  }

  test("all : Should get result as all feautres in the list could be completed") {
    val all = Future.all(List(Future("1"), Future("end")))
    try {
      Await.result(all, 1 second)
    } catch {
      case t: Exception => {
        assert(false)
      }
    }
  }


  test("any : Should get result as all feautres in the list could be completed") {
    val all = Future.any(List(Future("1"), Future("2"), Future("3")))
    val result = Await.result(all, 1 second)
    assert(result == "1" || result == "2" || result == "3")
  }

  test("delay : Future shouldn't be completed before delay") {
    val f = Future.delay(Duration(5, SECONDS))
    try {
      Await.result(f, 1 second)
      assert(false)
    } catch {
      case t: TimeoutException => {
        assert(!f.isCompleted)
      }
    }

  }

  test("delay : Future should be completed after delay") {
    val f = Future.delay(Duration(1, SECONDS))
    try {
      Await.result(f, 2 second)
      assert(f.isCompleted)
    } catch {
      case t: TimeoutException => {
        assert(false)
      }
    }

  }

  test("now : Future should be completed") {
    val p = Promise[Int]()
    p.success(1)
    val result = p.future.now
    assert(result == 1)
  }

  test("now : Not completed future should throw exception") {
    val p = Promise[Int]()
    try {
      p.future.now
      assert(false)
    } catch {
      case t: NoSuchElementException => {
        assert(!p.future.isCompleted)
      }
    }

  }

  test("continuewith : should return result") {
    val f1 = Future(3)
    val resultFeature = Await.result(f1.continueWith(f => Future(f.now * 2)), 1 second)
    val res = Await.result(resultFeature, 1 second)
    assert(res == 6)

  }

  test("continuewith : should return exception") {
    val f1 = Future(throw new NoSuchFieldException)
    try {
      val resultFeature = Await.result(f1.continueWith(p => Future(p.now.toString)), 1 second)
      val res = Await.result(resultFeature, 1 second)
      assert(false)
    } catch {
      case e: NoSuchFieldException => {
        assert(true)
      }
    }
  }


  test("continue : should return result") {
    val f1 = Future(3)
    val resultFeature = Await.result(f1.continue(tr => Future(tr.get * 2)), 1 second)
    val res = Await.result(resultFeature, 1 second)
    assert(res == 6)

  }

  test("continue : should return exception") {
    val f1 = Future(throw new NoSuchFieldException)


    try {
      val resultFeature = Await.result(f1.continue(tr => Future(tr.get)), 1 second)
      Await.result(resultFeature, 1 second)
      assert(false)
    } catch {
      case e: NoSuchFieldException => {
        assert(true)
      }
    }
  }


  test("CancellationTokenSource should allow stopping the computation") {
    val cts = CancellationTokenSource()
    val ct = cts.cancellationToken
    val p = Promise[String]()

    async {
      while (ct.nonCancelled) {
        // do work
      }

      p.success("done")
    }

    cts.unsubscribe()
    assert(Await.result(p.future, 1 second) == "done")
  }

  test("test Future.run") {
    import org.scalatest.concurrent.AsyncAssertions._
    import org.scalatest.time._

    val w = new Waiter

    val working = Future.run() { ct =>
      async {
        while (ct.nonCancelled) {
          Thread.sleep(200)
        }
        w { assert(true) }
        w.dismiss()
      }
    }

    Future.delay(1 seconds) onComplete {
      case _ => working.unsubscribe()
    }

    w.await(Timeout(Span(2, Seconds)))
  }

  class DummyExchange(val request: Request) extends Exchange {
    @volatile var response = ""
    val loaded = Promise[String]()

    def write(s: String) {
      response += s
    }

    def close() {
      loaded.success(response)
    }
  }

  class DummyListener(val port: Int, val relativePath: String) extends NodeScala.Listener {
    self =>

    @volatile private var started = false
    var handler: Exchange => Unit = null

    def createContext(h: Exchange => Unit) = this.synchronized {
      assert(started, "is server started?")
      handler = h
    }

    def removeContext() = this.synchronized {
      assert(started, "is server started?")
      handler = null
    }

    def start() = self.synchronized {
      started = true
      new Subscription {
        def unsubscribe() = self.synchronized {
          started = false
        }
      }
    }

    def emit(req: Request) = {
      val exchange = new DummyExchange(req)
      if (handler != null) handler(exchange)
      exchange
    }
  }

  class DummyServer(val port: Int) extends NodeScala {
    self =>
    val listeners = mutable.Map[String, DummyListener]()

    def createListener(relativePath: String) = {
      val l = new DummyListener(port, relativePath)
      listeners(relativePath) = l
      l
    }

    def emit(relativePath: String, req: Request) = this.synchronized {
      val l = listeners(relativePath)
      l.emit(req)
    }
  }

  test("Listener should serve the next request as a future") {
    val dummy = new DummyListener(8191, "/test")
    val subscription = dummy.start()

    def test(req: Request) {
      val f = dummy.nextRequest()
      dummy.emit(req)
      val (reqReturned, xchg) = Await.result(f, 1 second)

      assert(reqReturned == req)
    }

    test(immutable.Map("StrangeHeader" -> List("StrangeValue1")))
    test(immutable.Map("StrangeHeader" -> List("StrangeValue2")))

    subscription.unsubscribe()
  }

  test("Server should serve requests") {
    val dummy = new DummyServer(8191)
    val dummySubscription = dummy.start("/testDir") {
      request => for (kv <- request.iterator) yield (kv + "\n").toString
    }

    // wait until server is really installed
    Thread.sleep(500)

    def test(req: Request) {
      val webpage = dummy.emit("/testDir", req)
      val content = Await.result(webpage.loaded.future, 1 second)
      val expected = (for (kv <- req.iterator) yield (kv + "\n").toString).mkString
      assert(content == expected, s"'$content' vs. '$expected'")
    }

    test(immutable.Map("StrangeRequest" -> List("Does it work?")))
    test(immutable.Map("StrangeRequest" -> List("It works!")))
    test(immutable.Map("WorksForThree" -> List("Always works. Trust me.")))

    dummySubscription.unsubscribe()
  }

}




