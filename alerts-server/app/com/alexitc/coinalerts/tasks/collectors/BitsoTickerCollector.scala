package com.alexitc.coinalerts.tasks.collectors

import javax.inject.Inject

import com.alexitc.coinalerts.models._
import com.alexitc.coinalerts.services.external.BitsoService
import com.alexitc.coinalerts.tasks.models.Ticker

import scala.concurrent.Future

class BitsoTickerCollector @Inject() (bitsoService: BitsoService) extends TickerCollector {

  override val market: Market = Market.BITSO

  override def getTickerList: Future[List[Ticker]] = {
    bitsoService.getTickerList
  }
}
