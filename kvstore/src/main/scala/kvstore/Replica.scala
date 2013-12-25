package kvstore

import akka.actor._
import kvstore.Arbiter._
import java.util.Random
import scala.Some
import kvstore.Arbiter.Replicas
import kvstore.replication.{ReplicationController, GlobalReplicator}
import GlobalReplicator.KillMsgReplicator
import kvstore.persistence.PersisterController


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

  case class PersistedOk(id: Long, key: String, valueOption: Option[String], finalRecipient: ActorRef) extends OperationReply

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

  // to have references to ongoing repls
  var globalRepls = Set.empty[ActorRef]


  var seqNumber = 0

  //sending join message back to arbiter to register as Primary/Secondary replica
  arbiter ! Join

  def receive = {
    case JoinedPrimary => context.become(leader)
    case JoinedSecondary => {
      context.become(replica)
    }
  }

  /* Common Receive behaviour */
  val common: Receive = {
    case Get(key, id) => {
      sender ! GetResult(key, kv.get(key), id)
    }
  }

  // mapping for sent to persistent messages : id->(key,cancellable,sender)
  var awaitPersistFromLeader = Map.empty[Long, (Option[String], ActorRef)]

  // mapping for sent to persistentr messages : id->(key,seq)
  var awaitPersistFromReplica = Map.empty[Long, (String, Long, ActorRef)]


  /* Behavior for  the PRIMARY role. */
  val leader: Receive = ({
    case Insert(key, value, id) => {
      println(s"replica  : INSERT ${Insert(key, value, id)}")
      kv += (key -> value)
      awaitPersistFromLeader += (id ->(Option(value), sender))
      context.actorOf(PersisterController.props(sender, persistenceProps, Persist(key, Some(value), id)), "persist_control" + new Random().nextLong())
    }
    case Remove(key, id) => {
      println(s"replica  : REMOVE ${Remove(key, id)}")
      kv -= key
      awaitPersistFromLeader += (id ->(None, sender))
      context.actorOf(PersisterController.props(sender, persistenceProps, Persist(key, None, id)), "persist_control" + new Random().nextLong())
    }
    case Persisted(key, id) => {
      println(s"Replica  : $self got ${Persisted(key, id)}")
      val (value, recipient) = awaitPersistFromLeader(id)
      awaitPersistFromLeader -= id
      if (secondaries.isEmpty) {
        recipient ! OperationAck(id)
      }
      else {
        //start replicate controller
        println(s"Replica  : starting ReplicationController")
        val replController = system.actorOf(ReplicationController.props(id, recipient, replicators, Set(Replicate(key, value, new Random().nextLong()))))
        context.watch(replController)
        globalRepls += replController
      }

    }


    case Replicas(replicas) => {
      println(" Replica :  Replicas " + replicas)
      val messagesToSend: Set[Replicate] = createReplicateMsgFromKV()
      var replicatorsToSend = Set.empty[ActorRef]

      //terminate removed replicas
      val removedReplicas = secondaries.keySet diff replicas
      println(s"Replicas : removed replicas ${removedReplicas}")
      removedReplicas foreach (r => {
        //terminate replicator
        val replicatorToStop = secondaries(r)
        println(s"Replicas : replicatorToStop : ${replicatorToStop}")

        context.stop(replicatorToStop)
        secondaries -= r
        replicators -= replicatorToStop
        //TODO stop waiting for ack for terminated messgaes
        println(s"Replicas : ongoing global replicators : ${globalRepls}")
        globalRepls foreach (globRepl => {
          globRepl ! KillMsgReplicator(replicatorToStop)
        })
      })

      //
      replicas foreach (rep => {
        if (rep != self) {
          //already know it
          if (!secondaries.contains(rep)) {
            val replicator = system.actorOf(Replicator.props(rep), "rep_" + new Random().nextLong())
            secondaries += (rep -> replicator)
            replicators += replicator
            //ad all key values
            replicatorsToSend += replicator
          }
          else {
            // already have the replica
            replicatorsToSend += secondaries(rep)
          }
        }
      })
      // do we have messages to send?
      if (!messagesToSend.isEmpty && !replicatorsToSend.isEmpty) {
        println("Replicas : will send  " + messagesToSend)
        println("Replicas : will to  " + replicatorsToSend)
        val globalRepl = system.actorOf(GlobalReplicator.props(replicatorsToSend, messagesToSend))
        context.watch(globalRepl)
        println("Replicas  : adding ")
        globalRepls += globalRepl
      }


    }

    case Terminated(target) => {
      println(s"Replica : terminated $target")

      globalRepls -= target
    }

  }: Receive) orElse common
  // end PRIMARY behaviour

  def createReplicateMsgFromKV(): Set[Replicate] = {
    var messagesToSend = Set.empty[Replicate]
    kv.foreach(key_value => {
      val id = new Random().nextLong()
      messagesToSend = messagesToSend + Replicate(key_value._1, Option(key_value._2), id)
    })
    messagesToSend
  }

  /* Behavior for the REPLICA role. */
  val replica: Receive = ({
    case Snapshot(key, value, seq) => {
      println(s"Replica 2nd : receive ${Snapshot(key, value, seq)}")
      if (seq == seqNumber) {
        seqNumber += 1
        value match {
          case Some(s) => {
            kv += (key -> s)
            val id = new Random().nextLong()
            awaitPersistFromReplica += (id ->(key, seq, sender))
            context.actorOf(PersisterController.props(sender, persistenceProps, Persist(key, value, id)), "persist_control" + new Random().nextLong())
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
      val (_, seq, recipient) = awaitPersistFromReplica(id)
      awaitPersistFromReplica -= id
      recipient ! SnapshotAck(key, seq)
    }

  }: Receive) orElse common
  // end REPLICA role

}
