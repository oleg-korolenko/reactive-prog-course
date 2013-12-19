package kvstore

import akka.actor._
import akka.actor.ActorRef


object GlobalReplicationActor {

}

class GlobalReplicationActor(val awaitMsg: List[Int]) extends Actor {

  def receive = {
    case _=> ???
  }
}
