package suggestions

import org.scalatest._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import common.Solution

@RunWith(classOf[JUnitRunner])
class SolutionTest extends FunSuite {

  test("2*3 should give 6") {
    val result = Solution.solution("23*")
    assert(result ==6 , result)
  }

}
