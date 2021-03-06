package com.alexitc.coinalerts.models

import java.time.OffsetDateTime

import com.alexitc.coinalerts.core.WrappedLong
import play.api.libs.json._

case class DailyPriceAlert(
    id: DailyPriceAlertId,
    userId: UserId,
    market: Market,
    book: Book,
    createdOn: OffsetDateTime)

object DailyPriceAlert {
  implicit val writes: Writes[DailyPriceAlert] = Json.writes[DailyPriceAlert]
}

case class DailyPriceAlertId(long: Long) extends AnyVal with WrappedLong

case class CreateDailyPriceAlertModel(market: Market, book: Book)
object CreateDailyPriceAlertModel {
  implicit val reads: Reads[CreateDailyPriceAlertModel] = Json.reads[CreateDailyPriceAlertModel]
}
