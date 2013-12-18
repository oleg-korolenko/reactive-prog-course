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

  /* TODO Behavior for the Replicator. */
  def receive: Receive = {
    case Replicate(key, valueOption, id) => {
      val seq = nextSeq
      val cancellable = context.system.scheduler.schedule(0 milliseconds,
        100 milliseconds,
        replica,
        Snapshot(key, valueOption, seq))
      acks += (seq ->(sender, Replicate(key, valueOption, id), cancellable))

    }
    case SnapshotAck(key, seq) => {
      acks(seq)._3.cancel()
      val (actRef, replicate,_) = acks(seq)
      acks -= seq
      actRef ! Replicated(key, replicate.id)
    }
    case _ =>
  }

}
