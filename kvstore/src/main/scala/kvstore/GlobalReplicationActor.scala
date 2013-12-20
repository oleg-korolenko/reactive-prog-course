package kvstore

import kvstore.Replicator.{Replicated, Replicate}
import kvstore.GlobalReplicationActor.GlobalReplicate
import akka.actor._


object GlobalReplicationActor {

  case class GlobalReplicate(toReplicate: Map[ActorRef, List[Replicate]])
  case class GlobalReplicated(id:Long)
  def props(): Props = Props(new GlobalReplicationActor())
}

/**
 * Send a list of Replicates  different Replicators and checks for return
 * If all message were confirmed by Replciated - > kills himself

 */
class GlobalReplicationActor() extends Actor {

  def receive = {
    case GlobalReplicate(toReplicate) => {
      var messageToWaitFor = Set.empty[Long]
      toReplicate.foreach(key_value => {
        val replicator = key_value._1
        val messages = key_value._2
        messages foreach (message => {
          messageToWaitFor += message.id
          replicator ! message
        })
        context.become(awaitingForReplications(messageToWaitFor))
      })
    }
    case _ =>{
      throw new Exception()
    }
  }

  def awaitingForReplications(messageToWaitFor: Set[Long]): Receive = {
    case Replicated(key, id) => {
      val messagesLeft = messageToWaitFor - id
      if (messagesLeft.isEmpty) {
        self ! PoisonPill
      }
      context.become(awaitingForReplications(messagesLeft))
    }
    case _ =>{
      throw new Exception()
    }
  }
}
