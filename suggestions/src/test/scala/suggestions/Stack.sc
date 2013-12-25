package common

import scala.collection.mutable

object Solution {
  def solution(s: String): Int = {

    def calculate(toParse: List[Char], stack: mutable.Stack[Int]): Int = {

      toParse match {
        case Nil => {
          stack.size match {
            case 1 => stack.pop()
            case _ => -1
          }
        }
        case s :: xs => {
          s match {
            case '*' | '+' => stack.push(1)
            case _ => stack.push(2)
          }
          calculate(xs, stack)
        }
      }
      def operation(op: Char): Int = {
        if (stack.size != 2) throw new Exception("Not enough operands")
        var result: Long = 0
        op match {
          case '*' => result = stack.pop() * stack.pop()
          case '+' => result = stack.pop() + stack.pop()
        }
        if (result > Integer.MAX_VALUE) throw new Exception("Overflow")
        result.toInt
      }


    }
    calculate(s.toList, new mutable.Stack[Int]())
  }
}
