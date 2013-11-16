package simulations

import org.scalatest.FunSuite

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class CircuitSuite extends CircuitSimulator with FunSuite {
  val InverterDelay = 1
  val AndGateDelay = 3
  val OrGateDelay = 5

  test("andGate example") {
    val in1, in2, out = new Wire
    andGate(in1, in2, out)
    in1.setSignal(false)
    in2.setSignal(false)
    run

    assert(out.getSignal === false, "and 1")

    in1.setSignal(true)
    run

    assert(out.getSignal === false, "and 2")

    in2.setSignal(true)
    run

    assert(out.getSignal === true, "and 3")
  }


  test("orGate example") {
    val in1, in2, out = new Wire
    orGate(in1, in2, out)
    in1.setSignal(false)
    in2.setSignal(false)
    run

    assert(out.getSignal === false, "or 1")

    in1.setSignal(true)
    run

    assert(out.getSignal === true, "or 2")

    in2.setSignal(true)
    run

    assert(out.getSignal === true, "or 3")
  }

  test("orGate2 example") {
    val in1, in2, out = new Wire
    orGate2(in1, in2, out)
    in1.setSignal(false)
    in2.setSignal(false)
    run

    assert(out.getSignal === false, "or 1")

    in1.setSignal(true)
    run

    assert(out.getSignal === true, "or 2")

    in2.setSignal(true)
    run

    assert(out.getSignal === true, "or 3")
  }

  test("demux no control") {
    val in, out = new Wire
    demux(in, List(), List(out))
    in.setSignal(false)

    run

    assert(out.getSignal === false, "demux no control false")

    in.setSignal(true)
    run

    assert(out.getSignal === true, "demux no control false")
  }


  test("demux 1 to 2") {
    val in, control, out1, out2 = new Wire
    val output = List(out2, out1)
    demux(in, List(control), output)
    // in=false,control=false
    in.setSignal(false)
    control.setSignal(false)
    run
    assert(output.map(_.getSignal) === List(false, false), "demux 1 to 2 : in:false,control=false -> (false,false)")

    // in=false,control=true
    in.setSignal(false)
    control.setSignal(true)
    run
    assert(output.map(_.getSignal) === List(false, false), "demux 1 to 2 : in:false,control=true -> (false,false)")

    // in=true,control=false
    in.setSignal(true)
    control.setSignal(false)
    run
    assert(output.map(_.getSignal) === List(false, true), "demux 1 to 2 : in:tue,control=false -> (false,true)")
    // in=false,control=true
    in.setSignal(true)
    control.setSignal(true)
    run
    assert(output.map(_.getSignal) === List(true, false), "demux 1 to 2 : in:true,control=true -> (true,false)")
  }


  test("demux 2 to 4") {
    val in, c1, c0, out1, out2, out3, out0 = new Wire
    val output = List(out3, out2, out1, out0)
    demux(in, List(c1, c0), output)

    //IN=FALSE
    // in=false,c1=false, c2=false
    in.setSignal(false)
    c1.setSignal(false)
    c0.setSignal(false)
    run
    assert(output.map(_.getSignal) === List(false, false, false, false),
      "demux 2 to 4 : in:false,c1=false,c2=false -> (false,false,false,false)")

    // in=false,c1=false, c2=true
    in.setSignal(false)
    c1.setSignal(false)
    c0.setSignal(true)
    run
    assert(output.map(_.getSignal) === List(false, false, false, false),
      "demux 2 to 4 : in:false,c1=false,c2=true -> (false,false,false,false)")


    // in=false,c1=true, c2=false
    in.setSignal(false)
    c1.setSignal(true)
    c0.setSignal(false)
    run
    assert(output.map(_.getSignal) === List(false, false, false, false),
      "demux 2 to 4 : in:false,c1=true,c2=false -> (false,false,false,false)")


    // in=false,c1=true, c2=true
    in.setSignal(false)
    c1.setSignal(true)
    c0.setSignal(true)
    run
    assert(output.map(_.getSignal) === List(false, false, false, false),
      "demux 2 to 4 : in:false,c1=true,c2=true -> (false,false,false,false)")


    //IN=TRUE
    // in=true,c1=false, c2=false
    in.setSignal(true)
    c1.setSignal(false)
    c0.setSignal(false)
    run
    assert(output.map(_.getSignal) === List(false, false, false, true),
      "demux 2 to 4 : demux 2 to 4 : in:true,c1=false,c2=false -> (false,false,false,true)")

    // in=true,c1=false, c2=true
    in.setSignal(true)
    c1.setSignal(false)
    c0.setSignal(true)
    run
    assert(output.map(_.getSignal) === List(false, false, true, false),
      "demux 2 to 4 : in:true,c1=false,c2=true -> (false, false, true, false)")

    // in=true,c1=true, c2=false
    in.setSignal(true)
    c1.setSignal(true)
    c0.setSignal(false)
    run
    assert(output.map(_.getSignal) === List(false, true, false, false),
      "demux 2 to 4 : in:true,c1=true,c2=false -> (false, true, false, false)")

    // in=true,c1=true, c2=true
    in.setSignal(true)
    c1.setSignal(true)
    c0.setSignal(true)
    run
    assert(output.map(_.getSignal) === List(true, false, false, false),
      "demux 2 to 4 : in:true,c1=true,c2=true -> (true, false, false, false)")

  }


}
