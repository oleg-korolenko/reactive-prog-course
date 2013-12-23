package kvstore.replication

import kvstore.Replicator.{Replicated, Replicate}
import akka.actor._
import akka.actor
import kvstore.replication.GlobalReplicator.KillMsgReplicator


object GlobalReplicator {

  case class KillMsgReplicator(target: ActorRef)

  def props(replicators: Set[ActorRef], messages: Set[Replicate]): Props = Props(new GlobalReplicator(replicators, messages))
}

/**
 * Send a list of Replicates  different Replicators and checks for return
 * If all message were confirmed by Replciated - > kills himself

 */
class GlobalReplicator(replicators: Set[ActorRef], messages: Set[Replicate]) extends Actor {
  var msgReplicators = Map.empty[ActorRef, ActorRef]
  //TODO generate new message id
  println("Starting GlobalReplicator: param replicators=" + replicators)
  println("--------------replicators=" + replicators)
  println("--------------messages=" + messages)
  replicators foreach (repl => {
    messages foreach (msg => {

      val msgController = context.actorOf(MsgReplicator.props(repl, msg))
      context.watch(msgController)
      msgReplicators += repl -> msgController
    })
  })

  def receive: Actor.Receive = {
    case Terminated(_) => {
      println("GlobalReplicator :  Terminated. children left  =  "+context.children)
      if (context.children.isEmpty) {
        context.stop(self)
      }
    }
    case KillMsgReplicator(target) => {
      println("GlobalReplicator :  KillMsgReplicator")
      msgReplicators.keySet foreach (key => {
        if (key == target) {
         println("GlobalReplicator :  stopping "+msgReplicators(key))
          context.stop(msgReplicators(key))
          msgReplicators -= key
        }
      })
    }
  }
}
