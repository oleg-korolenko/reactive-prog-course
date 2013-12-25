import rx.lang.scala.Observable
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import rx.lang.scala._
import rx.lang.scala.subscriptions._
import scala.concurrent.{ExecutionContext, Future}
import scala.async.Async._
object Creation {
  def from[T](source: Iterable[T]): Observable[T] = {
    Observable(observer => {
      println("observer="+observer)
      source.foreach(observer.onNext(_))
      observer.onCompleted()
      // When you return an empty subscription
      // the alarm bells should go off.
      Subscription{}
    })
  }
}
val l = List(1, 2, 3)

val observable=Creation.from(l)

val observer  =observable.subscribe(onNext = println(_))














































