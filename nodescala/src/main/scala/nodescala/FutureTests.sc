import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import nodescala._
import scala.concurrent.duration._

val listOfFutures = List(Future("1"), Future(2 / 0), Future("end"))
val futureOfList = Future.all(listOfFutures)
val res = Await.result(futureOfList, 1 second)
//println(res)



