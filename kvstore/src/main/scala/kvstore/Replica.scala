package kvstore

import akka.actor._
import kvstore.Arbiter._
import akka.actor.SupervisorStrategy.Restart
import java.util.Random
import scala.concurrent.duration._
import scala.Some
import akka.actor.OneForOneStrategy
import kvstore.Arbiter.Replicas


object Replica {

  sealed trait Operation {
    def key: String

    def id: Long
  }

  case class Insert(key: String, value: String, id: Long) extends Operation

  case class Remove(key: String, id: Long) extends Operation

  case class Get(key: String, id: Long) extends Operation

  sealed trait OperationReply

  case class OperationAck(id: Long) extends OperationReply

  case class OperationFailed(id: Long) extends OperationReply

  case class GetResult(key: String, valueOption: Option[String], id: Long) extends OperationReply

  def props(arbiter: ActorRef, persistenceProps: Props): Props = Props(new Replica(arbiter, persistenceProps))
}

class Replica(val arbiter: ActorRef, persistenceProps: Props) extends Actor {

  import Replica._
  import Replicator._
  import Persistence._
  import context._
  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */

  var kv = Map.empty[String, String]
  // a map from secondary replicas to replicators
  var secondaries = Map.empty[ActorRef, ActorRef]
  // the current set of replicators
  var replicators = Set.empty[ActorRef]

  var seqNumber = 0

  //sending join message back to arbiter to register as Primary/Secondary replica
  arbiter ! Join

  def receive = {
    case JoinedPrimary => context.become(leader)
    case JoinedSecondary => context.become(replica)
  }

  /* TODO Behavior for  the leader role. */
  val leader: Receive = {
    // TODO failed receive or remove , 1 second - >OperationFailed
    case Insert(key, value, id) => {
      kv += (key -> value)
      //TODO send to all replicas

      sender ! OperationAck(id)
    }
    case Get(key, id) => {
      sender ! GetResult(key, kv.get(key), id)
    }
    case Remove(key, id) => {
      kv -= (key)
      sender ! OperationAck(id)
    }
    case Replicas(replicas) => {
      //TODO check all replicas , add/remove
      ???

    }
    case _ =>
  }

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _: PersistenceException => {
      //TODO what to do with an actor
      Restart

    }
    case _ => {
      println("")
      Restart
    }

  }

  // mapping for sent to persistentr messages : id->(key,seq)
  var sentForPersistence = Map.empty[Long, (String, Long, Cancellable,ActorRef)]
  /* TODO Behavior for the replica role. */
  val replica: Receive = {
    case Get(key, id) => {
      sender ! GetResult(key, kv.get(key), id)
    }
    case Snapshot(key, value, seq) => {
      if (seq == seqNumber) {
        seqNumber += 1
        value match {
          case Some(s) => {
            kv += (key -> s)

            val id = new Random().nextLong()

            val persister = context.actorOf(persistenceProps)
            //persister ! Persist(key, value, id)
            //sender ! SnapshotAck(key, seq)
            val cancellable = context.system.scheduler.schedule(0 milliseconds,
              100 milliseconds,
              persister,
              Persist(key, value, id))
            sentForPersistence += (id ->(key, seq, cancellable,sender))
          }
          case None => {
            kv -= key
            sender ! SnapshotAck(key, seq)
          }
        }
      }
      else if (seq < seqNumber) sender ! SnapshotAck(key, seq)
      //else we drop message

    }
    case Persisted(key, id) => {
      //TODO
      val (_, seq,cancellable,recipient) = sentForPersistence(id)
      cancellable.cancel()
      sentForPersistence -= (id)
      recipient ! SnapshotAck(key, seq)
    }
    case _ =>
  }

}