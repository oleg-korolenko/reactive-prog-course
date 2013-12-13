/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package actorbintree

import akka.actor.{Props, ActorRef, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, FlatSpec}
import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import org.scalatest.matchers.ShouldMatchers
import scala.util.Random
import scala.concurrent.duration._
import org.scalatest.FunSuite
import java.util.concurrent.TimeUnit


class BinaryTreeSuite(_system: ActorSystem) extends TestKit(_system) with FunSuite with ShouldMatchers with BeforeAndAfterAll with ImplicitSender {

  def this() = this(ActorSystem("PostponeSpec"))

  override def afterAll: Unit = system.shutdown()

  import actorbintree.BinaryTreeSet._

  def receiveN(requester: TestProbe, ops: Seq[Operation], expectedReplies: Seq[OperationReply]): Unit =
    within(5.seconds) {
      val repliesUnsorted = for (i <- 1 to ops.size) yield try {
        requester.expectMsgType[OperationReply]
      } catch {
        case ex: Throwable if ops.size > 10 => {
          //println(s" Operation  failure=${ops(i - 1)}")
          fail(s"failure to receive confirmation $i/${ops.size}", ex)

        }
        case ex: Throwable => {
          //println(s"Operation  failure=${ops(i - 1)}")
          fail(s"failure to receive confirmation $i/${ops.size}\nRequests:" + ops.mkString("\n    ", "\n     ", ""), ex)
        }
      }
      val replies = repliesUnsorted.sortBy(_.id)
      if (replies != expectedReplies) {
        val pairs = (replies zip expectedReplies).zipWithIndex filter (x => x._1._1 != x._1._2)
        fail("unexpected replies:" + pairs.map(x => s"at index ${x._2}: got ${x._1._1}, expected ${x._1._2}").mkString("\n    ", "\n    ", ""))
      }
    }

  def verify(probe: TestProbe, ops: Seq[Operation], expected: Seq[OperationReply]): Unit = {
    val topNode = system.actorOf(Props[BinaryTreeSet])

    ops foreach {
      op =>
        topNode ! op
    }

    receiveN(probe, ops, expected)
  }


  test("proper inserts and lookups") {
    val topNode = system.actorOf(Props[BinaryTreeSet])
    topNode ! Contains(testActor, id = 1, 1)
    expectMsg(ContainsResult(1, false))
    topNode ! Insert(testActor, id = 2, 1)
    topNode ! Contains(testActor, id = 3, 1)
    expectMsg(OperationFinished(2))
    expectMsg(ContainsResult(3, true))
  }



  test("proper inserts,removes and lookups") {
    val topNode = system.actorOf(Props[BinaryTreeSet])
    topNode ! Contains(testActor, id = 1, 1)
    expectMsg(ContainsResult(1, false))
    topNode ! Insert(testActor, id = 2, 1)
    expectMsg(OperationFinished(2))

    topNode ! Remove(testActor, id = 3, 1)
    expectMsg(OperationFinished(3))

    topNode ! Contains(testActor, id = 4, 1)
    expectMsg(ContainsResult(4, false))

    topNode ! Insert(testActor, id = 5, 1)
    expectMsg(OperationFinished(5))
    topNode ! Contains(testActor, id = 6, 1)
    expectMsg(ContainsResult(6, true))

  }

  /**
  test("a") {
    val topNode = system.actorOf(Props[BinaryTreeSet])
    topNode ! Remove(testActor, id = 0, 39)
    topNode ! Insert(testActor, id = 1, 51)
    topNode ! Contains(testActor, id = 2, 4)
    topNode ! Remove(testActor, id = 3, 21)
    topNode ! Insert(testActor, id = 4, 28)
    topNode ! Remove(testActor, id = 5, 21)
    topNode ! GC
    topNode ! Insert(testActor, id = 6, 90)
    topNode ! Insert(testActor, id = 7, 79)
    topNode ! Insert(testActor, id = 8, 26)
    topNode ! Insert(testActor, id = 9, 40)

    expectMsg(OperationFinished(0))
    expectMsg(OperationFinished(1))
    expectMsg(ContainsResult(2, false))
    expectMsg(OperationFinished(3))
    expectMsg(OperationFinished(4))
    expectMsg(OperationFinished(5))
    expectMsg(OperationFinished(6))
    expectMsg(OperationFinished(7))
    expectMsg(OperationFinished(8))
    expectMsg(OperationFinished(9))


  }
  */

  test("instruction example") {
    val requester = TestProbe()
    val requesterRef = requester.ref
    val ops = List(
      Insert(requesterRef, id = 100, 1),
      Contains(requesterRef, id = 50, 2),

      Remove(requesterRef, id = 10, 1),
      Insert(requesterRef, id = 20, 2),
      Contains(requesterRef, id = 80, 1),
      Contains(requesterRef, id = 70, 2)
    )

    val expectedReplies = List(
      OperationFinished(id = 10),
      OperationFinished(id = 20),
      ContainsResult(id = 50, false),
      ContainsResult(id = 70, true),
      ContainsResult(id = 80, false),
      OperationFinished(id = 100)
    )

    verify(requester, ops, expectedReplies)
  }



  test("my test") {
    val requester = TestProbe()
    val requesterRef = requester.ref
    val ops1 = List(

      Insert(requesterRef, id = 0, 80),

      Remove(requesterRef, id = 1, 47),


      Contains(requesterRef, id = 2, 19),
      Contains(requesterRef, id = 3, 94),
      Contains(requesterRef, id = 4, 45),
      Insert(requesterRef, id = 5, 30),
      Insert(requesterRef, id = 6, 86),

      Insert(requesterRef, id = 7, 56),
      Remove(requesterRef, id = 8, 97)
    )

    val expectedReplies = List(
      OperationFinished(id = 0),



       OperationFinished(id = 1),

      ContainsResult(id = 2, false),
      ContainsResult(id = 3, false),
      ContainsResult(id = 4, false),
      OperationFinished(id = 5),
      OperationFinished(id = 6),

      OperationFinished(id = 7),
      OperationFinished(id = 8)
    )

    val topNode = system.actorOf(Props[BinaryTreeSet])

    ops1 foreach {
      op =>
        topNode ! op
        if (op == Insert(requesterRef, id = 7, 56)) {
          topNode ! GC
        }
    }
    receiveN(requester, ops1, expectedReplies)


  }
  test("behave identically to built-in set (includes GC)") {
    val rnd = new Random()
    def randomOperations(requester: ActorRef, count: Int): Seq[Operation] = {
      def randomElement: Int = rnd.nextInt(100)
      def randomOperation(requester: ActorRef, id: Int): Operation = rnd.nextInt(4) match {
        case 0 => Insert(requester, id, randomElement)
        case 1 => Insert(requester, id, randomElement)
        case 2 => Contains(requester, id, randomElement)
        case 3 => Remove(requester, id, randomElement)
      }

      for (seq <- 0 until count) yield randomOperation(requester, seq)
    }

    def referenceReplies(operations: Seq[Operation]): Seq[OperationReply] = {
      var referenceSet = Set.empty[Int]
      def replyFor(op: Operation): OperationReply = op match {
        case Insert(_, seq, elem) =>
          referenceSet = referenceSet + elem
          OperationFinished(seq)
        case Remove(_, seq, elem) =>
          referenceSet = referenceSet - elem
          OperationFinished(seq)
        case Contains(_, seq, elem) =>
          ContainsResult(seq, referenceSet(elem))
      }

      for (op <- operations) yield replyFor(op)
    }

    val requester = TestProbe()
    val topNode = system.actorOf(Props[BinaryTreeSet])
    val count = 100000

    val ops = randomOperations(requester.ref, count)
    val expectedReplies = referenceReplies(ops)

    ops foreach {
      op =>
        //println(s"sending $op")
        topNode ! op
        if (rnd.nextDouble() < 0.1) {
          //println("sending GC")
          topNode ! GC
        }
    }
    receiveN(requester, ops, expectedReplies)
  }
}