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

  case class PersistTimeout(id: Long) extends OperationReply

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

  /* Common Receive behaviour */
  val common: Receive = {
    case Get(key, id) => {
      sender ! GetResult(key, kv.get(key), id)
    }
  }

  /* Behavior for  the leader role. */
  // mapping for sent to persistent messages : id->(key,cancellable,sender)
  var awaitPersistFromLeader = Map.empty[Long, (Cancellable, ActorRef)]
  var awaitReplicate = Map.empty[ActorRef, List[Long]]
  val leader: Receive = ({
    // TODO failed receive or remove , 1 second - >OperationFailed
    case Insert(key, value, id) => {
      kv += (key -> value)
      //TODO send to all Replicas via Replicators
      kv += (key -> value)
      val persister = context.actorOf(persistenceProps)
      val persistCancellable = context.system.scheduler.schedule(0 milliseconds,
        100 milliseconds,
        persister,
        Persist(key, Option(value), id))

      awaitPersistFromLeader += (id ->(persistCancellable, sender))
      // persist timeout
      context.system.scheduler.scheduleOnce(1 seconds,
        self,
        PersistTimeout(id))
    }
    case Remove(key, id) => {
      kv -= (key)
      sender ! OperationAck(id)
    }
    case Persisted(key, id) => {
      val (cancellable, recipient) = awaitPersistFromLeader(id)
      cancellable.cancel()
      awaitPersistFromLeader -= id
      recipient ! OperationAck(id)
    }

    case PersistTimeout(id) => {
      sender ! PoisonPill
      if (awaitPersistFromLeader.contains(id)) {
        //timeout of 1 second
        val (cancellable, recipient) = awaitPersistFromLeader(id)
        cancellable.cancel()
        awaitPersistFromLeader -= id
        recipient ! OperationFailed(id)
      }

    }
    case Replicas(replicas) => {
      var replToDoMap = Map.empty[ActorRef, List[Replicate]]
      replicas foreach (rep => {
        if (rep != self) {
          //already know it
          if (!secondaries.contains(rep)) {
            val replicator = system.actorOf(Replicator.props(rep), "rep_" + new Random().nextLong())
            secondaries += (rep -> replicator)
            replicators += replicator
            replToDoMap += replicator -> List.empty[Replicate]

            //send all key-value to replicator
            kv.foreach(key_value => {
              val id = new Random().nextLong()
              replToDoMap += replicator -> (replToDoMap(replicator) :+ Replicate(key_value._1, Option(key_value._2), id))
              //replicator ! Replicate(key_value._1, Option(key_value._2), id )

            })


          }

        }
      })
      //TODO check all replicas , add/remove
      ???

    }
  }: Receive) orElse common

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _: PersistenceException => {
      //TODO what to do with an actor
      Restart

    }
  }

  // mapping for sent to persistentr messages : id->(key,seq)
  var awaitPersistFromReplica = Map.empty[Long, (String, Long, Cancellable, ActorRef)]

  /* Behavior for the replica role. */
  val replica: Receive = ({
    case Snapshot(key, value, seq) => {
      if (seq == seqNumber) {
        seqNumber += 1
        value match {
          case Some(s) => {
            kv += (key -> s)
            val id = new Random().nextLong()
            val persister = context.actorOf(persistenceProps)
            val cancellable = context.system.scheduler.schedule(0 milliseconds,
              100 milliseconds,
              persister,
              Persist(key, value, id))
            awaitPersistFromReplica += (id ->(key, seq, cancellable, sender))
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
      val (_, seq, cancellable, recipient) = awaitPersistFromReplica(id)
      cancellable.cancel()
      awaitPersistFromReplica -= id
      recipient ! SnapshotAck(key, seq)
    }

  }: Receive) orElse common

}
