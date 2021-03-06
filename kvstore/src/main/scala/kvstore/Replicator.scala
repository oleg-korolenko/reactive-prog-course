package kvstore

import akka.actor._
import scala.concurrent.duration._


object Replicator {

  case class Replicate(key: String, valueOption: Option[String], id: Long)

  case class Replicated(key: String, id: Long)

  case class Snapshot(key: String, valueOption: Option[String], seq: Long)

  case class SnapshotAck(key: String, seq: Long)

  def props(replica: ActorRef): Props = Props(new Replicator(replica))
}

class Replicator(val replica: ActorRef) extends Actor {

  import Replicator._
  import Replica._
  import context.dispatcher

  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */

  // map from sequence number to pair of sender and request
  var acks = Map.empty[Long, (ActorRef, Replicate, Cancellable)]
  // a sequence of not-yet-sent snapshots (you can disregard this if not implementing batching)
  var pending = Vector.empty[Snapshot]

  var _seqCounter = 0L

  def nextSeq = {
    val ret = _seqCounter
    _seqCounter += 1
    ret
  }


  def receive: Receive = {
    case Replicate(key, valueOption, id) => {
      val seq = nextSeq
      println("Replicator :  for=" + Replicate(key, valueOption, id))
      println("Replicator :  snapshot=" + Snapshot(key, valueOption, seq))
      val cancellable = context.system.scheduler.schedule(0 milliseconds,
        100 milliseconds,
        replica,
        Snapshot(key, valueOption, seq))
      println(s"Replicator :  adding to acks =$seq")
      acks += (seq ->(sender, Replicate(key, valueOption, id), cancellable))

    }
    case SnapshotAck(key, seq) => {
      println(s"Replicators: received ${SnapshotAck(key, seq)} from $sender")
      println(s"Replicators: all acks ${acks.keySet}")
      println(s"Replicators: cancelling  $seq")
      if(acks.contains(seq)){
        acks(seq)._3.cancel()
        val (actRef, replicate, _) = acks(seq)
        actRef ! Replicated(key, replicate.id)
        acks -= seq
      }

    }
    case _ =>
  }

}
