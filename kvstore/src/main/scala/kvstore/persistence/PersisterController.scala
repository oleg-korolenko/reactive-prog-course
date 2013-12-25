package kvstore.persistence

import akka.actor._
import kvstore.Persistence.{Persisted, Persist}
import scala.concurrent.duration._
import java.util.Random
import kvstore.Replica._
import kvstore.Persistence.Persisted
import kvstore.Persistence.Persist
import akka.actor.SupervisorStrategy.Restart
import kvstore.Replica.OperationFailed
import kvstore.Persistence.Persisted
import kvstore.Persistence.Persist
import kvstore.Replica.OperationAck

object PersisterController {
  def props(recipient: ActorRef, targetProps:Props, msg: Persist): Props = Props(new PersisterController(recipient, targetProps, msg))
}

class PersisterController(receiver: ActorRef, targetProps:Props, msg: Persist) extends Actor {
  //println(s"starting PersisterController $self")
  println(s"START PersisterController ")
  println(s"---msg=$msg")
  println(s"---target=$targetProps")
  val msgPersister = context.actorOf(MsgPersister.props(targetProps, msg), "msg_persister" + new Random().nextLong())
  context.setReceiveTimeout(1 second)

  def receive: Actor.Receive = {
    case ReceiveTimeout => {
      //timeout - >stopping itself
      println(s"PersistenceController TIMEOUT")
      println(s"PersistenceController ${OperationFailed(msg.id)}")
      msgPersister ! PoisonPill
      receiver ! OperationFailed(msg.id)
      self ! PoisonPill
    }
    case Persisted(key, id) => {
     // println(s"PersisterController ($self) sending OperationAck  ${OperationAck(id)}")
      println(s"PersisterController sending OperationAck  ${OperationAck(id)}")
      msgPersister ! PoisonPill


      context.parent ! Persisted( key,id)
      //receiver ! OperationAck(id)
      //context.parent ! PersistedOk(id, key, msg.valueOption, receiver)
      self ! PoisonPill
    }
  }
}