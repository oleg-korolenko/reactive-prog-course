package kvstore

import akka.actor.{Props, ActorRef, Actor}
import kvstore.Replicator.{Replicated, Replicate}


object MsgReplicator {
  def props(replicator: ActorRef, msg: Replicate): Props = Props(new MsgReplicator(replicator, msg))
}

class MsgReplicator(replicator: ActorRef, msg: Replicate) extends Actor {
  println(s"starting Msg Replicator $self")
  println(s"---msg=$msg")
  println(s"---replicator=$replicator")
  replicator ! msg

  def receive: Actor.Receive = {
    case Replicated(key, id) => {
      println(s"MsgReplciator ($self) receive ${Replicated(key, id)}")
      context.stop(self)
    }
    case x => {
      throw new Exception()
    }

  }
}
