package kvstore.persistence

import akka.actor._
import kvstore.Persistence._
import scala.concurrent.duration._
import kvstore.Persistence.Persist
import akka.actor.OneForOneStrategy
import kvstore.Persistence.Persisted
import akka.actor.SupervisorStrategy.Restart

object MsgPersister {
  def props(targetProps:Props, msg: Persist): Props = Props(new MsgPersister(targetProps, msg))
}

class MsgPersister(targetProps:Props, msg: Persist) extends Actor {
  val msgId = msg.id
  val target  =context.actorOf(targetProps)
  //println(s"starting Msg Persister $self")
  println(s"START Msg Persister ")
  println(s"---msg=$msg")
  println(s"---persister=$target")
  target ! msg
  context.setReceiveTimeout(100 milliseconds)

  def receive: Actor.Receive = {
    case ReceiveTimeout => {
      //timeout - >stopping itself
      target ! msg
      context.setReceiveTimeout(100 milliseconds)
    }
    case Persisted(key, id) => {
    //  println(s"MsgPersister ($self) receive ${Persisted(key, id)}")
      println(s"MsgPersister receive ${Persisted(key, id)}")
      context.parent ! Persisted(key, id)
    }
    case x => {
      throw new Exception()
    }
  }

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _: PersistenceException => {
      println(s"MsgPersister  receive PersistenceException")
      Restart
    }
  }
}
