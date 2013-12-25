import concurrent.Promise
import scala.concurrent._
import ExecutionContext.Implicits.global
case class TaxCut(reduction: Int)
// either give the type as a type parameter to the factory method:
val taxcut = Promise[TaxCut]()

taxcut.success(TaxCut(20))
val assFuture=taxcut.future

assFuture.isCompleted
object Government {
  def redeemCampaignPledge(): Future[TaxCut] = {
    val p = Promise[TaxCut]()
    future {
      println("Starting the new legislative period.")
      Thread.sleep(2000)
      p.success(TaxCut(20))
      println("We reduced the taxes! You must reelect us!!!!1111")
    }
    p.future
  }
}

val usdQuote = future { println("coucou")}

//usdQuote





