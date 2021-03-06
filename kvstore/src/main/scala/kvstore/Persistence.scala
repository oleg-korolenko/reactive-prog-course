package kvstore

import akka.actor.{Props, Actor}
import scala.util.Random
import java.util.concurrent.atomic.AtomicInteger

object Persistence {
  case class Persist(key: String, valueOption: Option[String], id: Long)
  case class Persisted(key: String, id: Long)

  class PersistenceException extends Exception("Persistence failure")

  def props(flaky: Boolean): Props = Props(classOf[Persistence], flaky)
}

class Persistence(flaky: Boolean) extends Actor {
  import Persistence._

  def receive = {
    case Persist(key, value, id) =>
      println(s"Persistence : ${Persist(key, value, id)}")
      if (!flaky || Random.nextBoolean()) {
        sender ! Persisted(key, id)
        context.stop(self)
      }
      else {
        println(s"Persistencce : failure ${Persist(key, value, id)}")
        throw new PersistenceException
      }
  }

}
