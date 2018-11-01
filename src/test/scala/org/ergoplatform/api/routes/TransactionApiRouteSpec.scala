package org.ergoplatform.api.routes

import java.net.InetSocketAddress

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit.TestDuration
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.syntax._
import org.ergoplatform.api.TransactionsApiRoute
import org.ergoplatform.modifiers.mempool.ErgoTransaction
import org.ergoplatform.settings.Constants
import org.ergoplatform.utils.{BoxUtils, Stubs}
import org.ergoplatform.{ErgoBox, ErgoBoxCandidate, Input}
import org.scalatest.{FlatSpec, Matchers}
import scorex.core.settings.RESTApiSettings
import scorex.crypto.authds.ADKey
import sigmastate.interpreter.{ContextExtension, ProverResult}

import scala.concurrent.duration._

class TransactionApiRouteSpec extends FlatSpec
  with Matchers
  with ScalatestRouteTest
  with Stubs
  with FailFastCirceSupport {

  val prefix = "/transactions"

  val restApiSettings = RESTApiSettings(new InetSocketAddress("localhost", 8080), None, None, 10.seconds)
  val route: Route = TransactionsApiRoute(readersRef, nodeViewRef, restApiSettings).route

  implicit val timeout: RouteTestTimeout = RouteTestTimeout(15.seconds.dilated)

  val input = Input(
    ADKey @@ Array.fill(ErgoBox.BoxId.size)(0: Byte),
    ProverResult(Array.emptyByteArray, ContextExtension(Map())))

  val boxValue: Long = BoxUtils.minimalErgoAmountSimulated(Constants.TrueLeaf)
  val output: ErgoBoxCandidate = new ErgoBoxCandidate(boxValue, Constants.TrueLeaf,
    creationHeight = creationHeightGen.sample.get)
  val tx: ErgoTransaction = ErgoTransaction(IndexedSeq(input), IndexedSeq(output))

  it should "post transaction" in {
    Post(prefix, tx.asJson) ~> route ~> check {
      status shouldBe StatusCodes.OK
      responseAs[String] shouldEqual tx.id
    }
  }

  it should "get unconfirmed from mempool" in {
    Get(prefix + "/unconfirmed") ~> route ~> check {
      status shouldBe StatusCodes.OK
      memPool.take(50).toSeq shouldBe responseAs[Seq[ErgoTransaction]]
    }
  }

  it should "get unconfirmed from mempool using limit and offset" in {
    val limit = 10
    val offset = 20
    Get(prefix + s"/unconfirmed?limit=$limit&offset=$offset") ~> route ~> check {
      status shouldBe StatusCodes.OK
      memPool.unconfirmed.toSeq.slice(offset, offset + limit) shouldBe responseAs[Seq[ErgoTransaction]]
    }
  }

}
