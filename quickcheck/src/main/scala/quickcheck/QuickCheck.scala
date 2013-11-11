package quickcheck


import org.scalacheck._
import Arbitrary._
import Gen._
import Prop._

abstract class QuickCheckHeap extends Properties("Heap") with IntHeap {

  lazy val genHeap: Gen[H] = {
    for {
      i <- arbitrary[Int]
      h <- Gen.frequency((1, value(empty)), (10, genHeap))
    } yield insert(i, h)
  }


  implicit lazy val arbHeap: Arbitrary[H] = Arbitrary(genHeap)


  property("min with generator") = forAll(genHeap) {
    (h: H) =>
      if (!isEmpty(h)) {
        val m = findMin(h)
        findMin(insert(m, h)) == m
      }
      else true

  }


  property("delete min with generator") = forAll(genHeap) {
    h: H =>
      if (!isEmpty(h)) {

        //println("initial heap = " + h)
        val min = findMin(h)
        val withoutMin = deleteMin(h)
        //println("withoutMin  = " + withoutMin)
        if (!isEmpty(withoutMin)) findMin(withoutMin) >= min else true
      }
      else true

  }


  property("delete min from heap with 2 elements") = forAll {
    (a: Int, b: Int) =>
      val heapWith1elem = insert(a, empty)
      val heapWith2elems = insert(b, heapWith1elem)
      //println("heap with 2 elems = " + heapWith2elems)
      //println("after delete  with 2 elems = " + deleteMin(heapWith2elems))
      if (a <= b) {
        deleteMin(heapWith2elems) == insert(b, empty)
      }
      else deleteMin(heapWith2elems) == insert(a, empty)
  }

  property("continually find mins and  delete") = forAll(genHeap) {
    h: H =>
      recurciveCheck(h)

  }

  def recurciveCheck(h: H): Boolean =(isEmpty(h)||isEmpty(deleteMin(h))) || (ord.lteq(findMin(h), findMin(deleteMin(h))) && (recurciveCheck(deleteMin(h))))



  property("delete min from heap with 2 elements") = forAll {
    (a: Int, b: Int) =>
      findMin(deleteMin(insert(3, insert(2, insert(1, empty)))))!=1
  }

  property("meld 2 heaps") = forAll(genHeap, genHeap) {
    (h1: H, h2: H) =>
      val minH1 = findMin(h1)
      val minH2 = findMin(h2)
      val minMeld = findMin(meld(h1, h2))
      minMeld == minH1 || minMeld == minH2
  }


}
