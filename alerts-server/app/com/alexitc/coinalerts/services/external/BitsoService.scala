package com.alexitc.coinalerts.services.external

import javax.inject.Inject

import com.alexitc.coinalerts.models.Book
import com.alexitc.coinalerts.tasks.models.Ticker
import com.bitso.{Bitso, BitsoTicker}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class BitsoService @Inject() (bitso: Bitso)(implicit ec: ExecutionContext) {

  private val logger = LoggerFactory.getLogger(this.getClass)

  def availableBooks: Future[List[Book]] = Future {
    bitso.getTicker.toList.flatMap { ticker =>
      Book.fromString(ticker.getBook)
          .orElse {
            logger.warn(s"Unable to create book from string = [${ticker.getBook}]")
            None
          }
    }
  }

  def getTickerList: Future[List[Ticker]] = Future {
    bitso.getTicker
        .toList
        .flatMap(createTicker)
  }

  private def createTicker(bitsoTicker: BitsoTicker): Option[Ticker] = {
    Book.fromString(bitsoTicker.getBook)
        .map { book =>
          Ticker(book, bitsoTicker.getLast)
        }
        .orElse {
          logger.warn(s"Unable to create book from string = [${bitsoTicker.getBook}]")
          None
        }
  }
}
