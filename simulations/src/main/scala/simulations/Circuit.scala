package simulations

import common._

class Wire {
  private var sigVal = false
  private var actions: List[Simulator#Action] = List()

  def getSignal: Boolean = sigVal

  def setSignal(s: Boolean) {
    if (s != sigVal) {
      sigVal = s
      actions.foreach(action => action())
    }
  }

  def addAction(a: Simulator#Action) {
    actions = a :: actions
    a()
  }
}

abstract trait CircuitSimulator extends Simulator {

  val InverterDelay: Int
  val AndGateDelay: Int
  val OrGateDelay: Int

  def probe(name: String, wire: Wire) {
    wire addAction {
      () => afterDelay(0) {
        println(
          "  " + currentTime + ": " + name + " -> " + wire.getSignal)
      }
    }
  }

  def inverter(input: Wire, output: Wire) {
    def invertAction() {
      val inputSig = input.getSignal
      afterDelay(InverterDelay) {
        output.setSignal(!inputSig)
      }
    }
    input addAction invertAction
  }

  def andGate(a1: Wire, a2: Wire, output: Wire) {
    def andAction() {
      val a1Sig = a1.getSignal
      val a2Sig = a2.getSignal
      afterDelay(AndGateDelay) {
        output.setSignal(a1Sig & a2Sig)
      }
    }
    a1 addAction andAction
    a2 addAction andAction
  }


  def orGate(a1: Wire, a2: Wire, output: Wire) {
    def orAction() {
      val a1Sig = a1.getSignal
      val a2Sig = a2.getSignal
      afterDelay(OrGateDelay) {
        output.setSignal(a1Sig | a2Sig)
      }
    }
    a1 addAction orAction
    a2 addAction orAction

  }

  def orGate2(a1: Wire, a2: Wire, output: Wire) {
    def or2Action() {
      val notA1, notA2, notOuput = new Wire
      inverter(a1, notA1)
      inverter(a2, notA2)
      andGate(notA1, notA2, notOuput)
      andGate(notA1, notA2, notOuput)
      inverter(notOuput, output)
    }
    a1 addAction or2Action
    a2 addAction or2Action
  }

  def demux(in: Wire, c: List[Wire], out: List[Wire]) {


    def demuxAction() {
      val inSignal = in.getSignal
      c match {
        case Nil => out.head.setSignal(inSignal)
        case control :: Nil => {
          val invertedControl, out1, out2 = new Wire
          inverter(control, invertedControl)
          andGate(in, invertedControl, out1)
          andGate(in, c.head, out2)

          demux(out2, c.tail, List(out.head))
          demux(out1, c.tail, List(out.tail.head))

        }
        case _ => {
          val invertedControl, out1, out2 = new Wire
          inverter(c.head, invertedControl)
          andGate(in, invertedControl, out1)
          andGate(in, c.head, out2)

          demux(out1, c.tail, out.takeRight(out.size / 2))
          demux(out2, c.tail, out.take(out.size / 2))
        }
      }

    }
    in addAction demuxAction

  }

}

object Circuit extends CircuitSimulator {
  val InverterDelay = 1
  val AndGateDelay = 3
  val OrGateDelay = 5

  def andGateExample {
    val in1, in2, out = new Wire
    andGate(in1, in2, out)
    probe("in1", in1)
    probe("in2", in2)
    probe("out", out)
    in1.setSignal(false)
    in2.setSignal(false)
    run

    in1.setSignal(true)
    run

    in2.setSignal(true)
    run
  }

  //
  // to complete with orGateExample and demuxExample...
  //
}

object CircuitMain extends App {
  // You can write tests either here, or better in the test class CircuitSuite.
  Circuit.andGateExample
}
