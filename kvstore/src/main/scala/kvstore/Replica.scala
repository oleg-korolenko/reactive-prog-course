package kvstore

import akka.actor._
import kvstore.Arbiter._
import akka.actor.SupervisorStrategy.Restart
import java.util.Random
import scala.concurrent.duration._
import scala.Some
import akka.actor.OneForOneStrategy
import kvstore.Arbiter.Replicas
import kvstore.GlobalReplicationActor.{GlobalReplicated, GlobalReplicate}


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

  case class ReplicateFailed(id: Long) extends OperationReply

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


  def sendToReplicas(msg: Replicate) {
    //TODO send message to replicas
  }


  def scheduleOnceOperationReply(timeout: FiniteDuration,msg: OperationReply) {
    context.system.scheduler.scheduleOnce(timeout,
      self,
      msg)
  }

  def createScheduledPersister(msg: Persist): Cancellable = {
    val persister = context.actorOf(persistenceProps)
    context.system.scheduler.schedule(0 milliseconds,
      100 milliseconds,
      persister, msg
    )
  }

  /* Common Receive behaviour */
  val common: Receive = {
    case Get(key, id) => {
      sender ! GetResult(key, kv.get(key), id)
    }
  }

  // mapping for sent to persistent messages : id->(key,cancellable,sender)
  var awaitPersistFromLeader = Map.empty[Long, (Operation, Cancellable, ActorRef)]

  // mapping for sent to persistentr messages : id->(key,seq)
  var awaitPersistFromReplica = Map.empty[Long, (String, Long, Cancellable, ActorRef)]

  var awaitReplicate = Map.empty[ActorRef, List[Long]]

  /* Behavior for  the PRIMARY role. */
  val leader: Receive = ({
    // TODO failed receive or remove , 1 second - >OperationFailed
    case Insert(key, value, id) => {
      kv += (key -> value)
      // persist every 100ms
      val persistCancellable = createScheduledPersister(Persist(key, Some(value), id))
      awaitPersistFromLeader += (id ->(Insert(key, value, id), persistCancellable, sender))

      // persist timeout
      scheduleOnceOperationReply(1 seconds,PersistTimeout(id))
    }
    case Remove(key, id) => {
      kv -= (key)
      //persist every 100 ms
      val persistCancellable = createScheduledPersister(Persist(key, None, id))
      awaitPersistFromLeader += (id ->(Remove(key, id), persistCancellable, sender))

      // persist timeout 1s
      scheduleOnceOperationReply(1 seconds,PersistTimeout(id))
    }
    case Persisted(key, id) => {
      val (operation, cancellable, recipient) = awaitPersistFromLeader(id)
      cancellable.cancel()
      awaitPersistFromLeader -= id
      if (secondaries.isEmpty) {
        recipient ! OperationAck(id)
      }
      else {
        //no OperationAck waiting for Replicated
        secondaries.foreach(key_value => {
          replicators foreach (r => r ! Replicate(key, Option(operation.key), new Random().nextLong()))
        })
        // TODO start replication actor
      }


    }
    case GlobalReplicated(id) => {
      // GlobalReplica
    }
    case PersistTimeout(id) => {
      sender ! PoisonPill
      if (awaitPersistFromLeader.contains(id)) {
        //timeout of 1 second
        val (operation, cancellable, recipient) = awaitPersistFromLeader(id)
        cancellable.cancel()
        awaitPersistFromLeader -= id
        recipient ! OperationFailed(id)
      }

    }
    case Replicas(replicas) => {
      var replToDoMap = Map.empty[ActorRef, List[Replicate]]
      //getting a list messages to send
      var messagesToSend = List.empty[Replicate]
      kv.foreach(key_value => {
        val id = new Random().nextLong()
        messagesToSend = messagesToSend :+ Replicate(key_value._1, Option(key_value._2), id)
      })
      replicas foreach (rep => {
        if (rep != self) {
          //already know it
          if (!secondaries.contains(rep)) {
            val replicator = system.actorOf(Replicator.props(rep), "rep_" + new Random().nextLong())
            secondaries += (rep -> replicator)
            replicators += replicator
            //ad all key values
            replToDoMap += replicator -> messagesToSend
          }

        }

      })

      val globalReplicator = system.actorOf(GlobalReplicationActor.props(), "global_rep_" + new Random().nextLong())
      globalReplicator ! GlobalReplicate(replToDoMap)
      //TODO check all replicas , add/remove
    }
  }: Receive) orElse common
  // end PRIMARY behaviour


  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _: PersistenceException => {
      //TODO what to do with an actor
      Restart
    }
  }


  /* Behavior for the REPLICA role. */
  val replica: Receive = ({
    case Snapshot(key, value, seq) => {
      if (seq == seqNumber) {
        seqNumber += 1
        value match {
          case Some(s) => {
            kv += (key -> s)
            val id = new Random().nextLong()
            val cancellable = createScheduledPersister(Persist(key, value, id))
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
  // end REPLICA role

}
