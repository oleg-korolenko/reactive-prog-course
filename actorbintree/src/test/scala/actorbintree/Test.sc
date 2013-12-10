
import scala.collection.immutable.Queue

var q = Queue.empty[String]
q = q.enqueue("1")
q = q.enqueue("3")
q = q.enqueue("2")

 q.foreach(x=>println(x))



