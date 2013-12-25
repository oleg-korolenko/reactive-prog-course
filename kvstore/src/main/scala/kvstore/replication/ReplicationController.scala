package kvstore.replication

import akka.actor._
import scala.concurrent.duration._
import akka.actor.Terminated
import kvstore.Replica.OperationFailed
import kvstore.Replica.OperationAck
import kvstore.Replicator.Replicate
import java.util.Random
import kvstore.replication.GlobalReplicator.KillMsgReplicator

object ReplicationController {

  def props(id: Long, recipient: ActorRef, replicators: Set[ActorRef], messages: Set[Replicate]): Props = Props(new ReplicationController(id, recipient, replicators, messages))
}

class ReplicationController(id: Long, recipient: ActorRef, replicators: Set[ActorRef], messages: Set[Replicate]) extends Actor {
  println("START Replication controller with ")
  println("---- msgId " + id)
  println("---- messages" + messages)
  println("---- replicators" + replicators)
  context.watch(context.actorOf(GlobalReplicator.props(replicators, messages), "global_rep_" + new Random().nextLong()))
  context.setReceiveTimeout(1 second)

  def receive: Actor.Receive = {
    case ReceiveTimeout => {
      println(s"ReplicationController : receiveTimeout for $id ")
      recipient ! OperationFailed(id)
      context.stop(self)
    }
    case KillMsgReplicator(target) => {
      println("Replication controller :  KillMsgReplicator")
      context.children foreach (child => child ! KillMsgReplicator(target))
    }
    case Terminated(_) => {
      //global replication is finished
      println(s"ReplicationController :  sending ${OperationAck(id)}")
      recipient ! OperationAck(id)
      self ! PoisonPill
    }
  }
}
