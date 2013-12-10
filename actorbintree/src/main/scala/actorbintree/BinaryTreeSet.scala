/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package actorbintree

import akka.actor._
import scala.collection.immutable.Queue
import scala.util.Random

object BinaryTreeSet {

  trait Operation {
    def requester: ActorRef

    def id: Int

    def elem: Int
  }

  trait OperationReply {
    def id: Int
  }

  /** Request with identifier `id` to insert an element `elem` into the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Insert(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to check whether an element `elem` is present
    * in the tree. The actor at reference `requester` should be notified when
    * this operation is completed.
    */
  case class Contains(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to remove the element `elem` from the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Remove(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request to perform garbage collection */
  case object GC

  /** Holds the answer to the Contains request with identifier `id`.
    * `result` is true if and only if the element is present in the tree.
    */
  case class ContainsResult(id: Int, result: Boolean) extends OperationReply

  /** Message to signal successful completion of an insert or remove operation. */
  case class OperationFinished(id: Int) extends OperationReply

}


class BinaryTreeSet extends Actor {

  import BinaryTreeSet._
  import BinaryTreeNode._

  def createRoot: ActorRef = context.actorOf(BinaryTreeNode.props(0, initiallyRemoved = true))

  var root = createRoot

  // optional
  var pendingQueue = Queue.empty[Operation]

  // optional
  def receive = normal

  // optional
  /** Accepts `Operation` and `GC` messages. */
  val normal: Receive = {
    case Insert(requester: ActorRef, id: Int, elem: Int) => root ! Insert(requester: ActorRef, id: Int, elem: Int)
    case Contains(requester: ActorRef, id: Int, elem: Int) => root ! Contains(requester: ActorRef, id: Int, elem: Int)
    case Remove(requester: ActorRef, id: Int, elem: Int) => root ! Remove(requester: ActorRef, id: Int, elem: Int)
    case GC => {
      val newRoot = createRoot
      context.become(garbageCollecting(newRoot))
      root ! CopyTo(newRoot)
    }
    case x => println("yahoo  : " + x)
  }

  // optional
  /** Handles messages while garbage collection is performed.
    * `newRoot` is the root of the new binary tree where we want to copy
    * all non-removed elements into.
    */
  def garbageCollecting(newRoot: ActorRef): Receive = {
    case GC => {}
    case CopyFinished => {
      try {
        println(" treeset copy finished from "+sender)
        println(" root "+root)
        println(" newroot "+newRoot)
        pendingQueue foreach (el => {
          println(s"from queue = $el")
          newRoot ! el
        })
        pendingQueue = Queue.empty[Operation]
        root ! PoisonPill
        root = newRoot
        context.become(normal)
      }
      catch {
        case e: Exception => println("oups" + e)
      }
    }
    case o: Operation => {
      println(s"enqueue $o")
      pendingQueue = pendingQueue.enqueue(o)
    }
    case x => println("yahoo  : " + x)
  }

}

object BinaryTreeNode {

  trait Position

  case object Left extends Position

  case object Right extends Position

  case class CopyTo(treeNode: ActorRef)

  case object CopyFinished

  def props(elem: Int, initiallyRemoved: Boolean) = Props(classOf[BinaryTreeNode], elem, initiallyRemoved)
}

class BinaryTreeNode(val elem: Int, initiallyRemoved: Boolean) extends Actor {

  import BinaryTreeNode._
  import BinaryTreeSet._

  var subtrees = Map[Position, ActorRef]()
  var removed = initiallyRemoved

  // optional
  def receive = normal

  // optional
  /** Handles `Operation` messages and `CopyTo` requests. */
  val normal: Receive = {
    case Insert(requester: ActorRef, id: Int, elem: Int) => {
      try {
        if (elem != this.elem) {
          val pos = if (elem < this.elem) Left else Right
          if (subtrees.contains(pos)) {
            subtrees(pos) ! Insert(requester: ActorRef, id: Int, elem: Int)
          }
          else {
            val newNode = context.actorOf(props(elem, false))
            subtrees = subtrees + (pos -> newNode)
            requester ! OperationFinished(id)
            //  println(OperationFinished(id))
          }

        }
        else {
          //elem equal to current node value
          if (removed) removed = false
          requester ! OperationFinished(id)
        }
      }
      catch {
        case e: Exception => println("oups" + e)
      }
    }
    case Contains(requester: ActorRef, id: Int, elem: Int) => {
      if (elem != this.elem) {
        val pos = if (elem < this.elem) Left else Right
        if (subtrees.contains(pos)) {
          subtrees(pos) ! Contains(requester: ActorRef, id: Int, elem: Int)
        }
        else requester ! ContainsResult(id, false)
      }
      else {
        //elem equal to current node value
        requester ! ContainsResult(id, !removed)
      }
    }
    case Remove(requester: ActorRef, id: Int, e: Int) => {
      if (e != this.elem) {
        val pos = if (e < this.elem) Left else Right
        if (subtrees.contains(pos)) {
          subtrees(pos) ! Remove(requester: ActorRef, id: Int, e: Int)
        }
        else {
          requester ! OperationFinished(id)
          // println(OperationFinished(id))
        }
      }
      else {
        //elem equal to current node value
        this.removed = true
        requester ! OperationFinished(id)
        // println(OperationFinished(id))
      }
    }
    case CopyTo(newT: ActorRef) => {
      try {
        if (subtrees.isEmpty && removed) {
          context.parent ! CopyFinished
        }
        else {
          context.become(copying(subtrees.values.toSet, removed))
          subtrees.values foreach (n => n ! CopyTo(newT))
          if (!removed) newT ! Insert(self, Random.nextInt(), elem)
        }
      }
      catch {
        case e: Exception => println("oups" + e)
      }
    }
    case x => println("yahoo  : " + x)
  }

  // optional
  /** `expected` is the set of ActorRefs whose replies we are waiting for,
    * `insertConfirmed` tracks whether the copy of this node to the new tree has been confirmed.
    */
  def copying(expected: Set[ActorRef], insertConfirmed: Boolean): Receive = {
    case CopyFinished => {
      try {
        val expectedLeft = expected - sender
        if (expectedLeft.isEmpty) {
          if (insertConfirmed) copyFinished()
        }
        else context.become(copying(expectedLeft, insertConfirmed))
      }
      catch {
        case e: Exception => println("oups" + e)
      }
    }
    case OperationFinished(id: Int) => {
      try {
        if (expected.isEmpty) copyFinished()
        else context.become(copying(expected, true))
      }
      catch {
        case e: Exception => println("oups" + e)
      }
    }
    case x => println("yahoo  : " + x)
  }

  def copyFinished() {
    context.become(normal)
    context.parent ! CopyFinished

  }

}
