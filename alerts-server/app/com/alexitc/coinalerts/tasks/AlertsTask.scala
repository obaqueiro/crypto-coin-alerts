package com.alexitc.coinalerts.tasks

import javax.inject.Inject

import com.alexitc.coinalerts.commons.FutureOr.Implicits.FutureOps
import com.alexitc.coinalerts.config.TaskExecutionContext
import com.alexitc.coinalerts.data.async.{FixedPriceAlertFutureDataHandler, UserFutureDataHandler}
import com.alexitc.coinalerts.models._
import com.alexitc.coinalerts.services.{EmailMessagesProvider, EmailServiceTrait, EmailText}
import com.alexitc.coinalerts.tasks.collectors.{BitsoTickerCollector, BittrexTickerCollector}
import com.alexitc.coinalerts.tasks.models.FixedPriceAlertEvent
import org.scalactic.{Bad, Good}
import org.slf4j.LoggerFactory
import play.api.i18n.{Lang, MessagesApi}

import scala.concurrent.Future
import scala.util.control.NonFatal

class AlertsTask @Inject() (
    alertCollector: FixedPriceAlertCollector,
    bitsoTickerCollector: BitsoTickerCollector,
    bittrexAlertCollector: BittrexTickerCollector,
    userDataHandler: UserFutureDataHandler,
    alertDataHandler: FixedPriceAlertFutureDataHandler,
    emailMessagesProvider: EmailMessagesProvider,
    messagesApi: MessagesApi,
    emailServiceTrait: EmailServiceTrait)(
    implicit ec: TaskExecutionContext) {

  private val logger = LoggerFactory.getLogger(this.getClass)

  private val tickerCollectorList = List(bitsoTickerCollector, bittrexAlertCollector)

  def execute(): Future[Unit] = {
    val futures = tickerCollectorList.map { tickerCollector =>
      alertCollector.collect(tickerCollector)
    }

    Future.sequence(futures)
        .map(_.flatten)
        .map(groupByUser)
        .flatMap { userAlerts =>
          userAlerts.foreach {
            case (userId, eventList) => triggerAlerts(userId, eventList)
          }

          Future.unit
        }
  }

  private def groupByUser(eventList: List[FixedPriceAlertEvent]): Map[UserId, List[FixedPriceAlertEvent]] = {
    eventList.groupBy(_.alert.userId)
  }

  private def triggerAlerts(userId: UserId, eventList: List[FixedPriceAlertEvent]): Future[Unit] = {
    val result = for {
      user <- userDataHandler.getVerifiedUserById(userId).toFutureOr
      preferences <- userDataHandler.getUserPreferences(userId).toFutureOr
      _ <- {
        val emailSubject = emailMessagesProvider.yourAlertsSubject(preferences.lang)
        val emailText = createEmailText(eventList)(preferences.lang)
        emailServiceTrait.sendEmail(user.email, emailSubject, emailText).toFutureOr
      }
    } yield eventList.foreach { event =>
      alertDataHandler.markAsTriggered(event.alert.id)
    }

    result.toFuture.map {
      case Good(_) => ()
      case Bad(errors) =>
        logger.error(s"Error while trying to send alerts by email to user = [${userId.string}], errors = [$errors]")

    }.recover {
      case NonFatal(ex) =>
        logger.error(s"Error while trying to send alerts by email to user = [${userId.string}]", ex)
    }
  }

  private def groupByMarket(eventList: List[FixedPriceAlertEvent]): Map[Market, List[FixedPriceAlertEvent]] = {
    eventList.groupBy(_.alert.market)
  }

  private def createEmailText(eventList: List[FixedPriceAlertEvent])(implicit lang: Lang): EmailText = {
    val text = groupByMarket(eventList)
        .map {
          case (market, marketEvents) =>
            val marketLines = marketEvents.map(createText).mkString("\n")
            s"${market.string}:\n$marketLines"
        }
        .mkString("\n\n\n")

    new EmailText(text)
  }

  private def createText(event: FixedPriceAlertEvent)(implicit lang: Lang): String = {
    val alert = event.alert

    val percentageDifferenceMaybe = alert.basePrice.map { basePrice =>
      val percentage = 100 * (1 - (basePrice max event.currentPrice) / (basePrice min event.currentPrice))
      percentage
    }

    val message = if (alert.isGreaterThan) {
      messagesApi("message.alert.priceIncreased", alert.book.string, event.currentPrice)
    } else {
      messagesApi("message.alert.priceDecreased", alert.book.string, event.currentPrice)
    }

    percentageDifferenceMaybe.map { percent =>
      val readablePercent = percent.toString()
      s"$message ($readablePercent %)"
    }.getOrElse {
      message
    }
  }
}
