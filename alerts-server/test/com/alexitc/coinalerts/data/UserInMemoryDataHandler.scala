package com.alexitc.coinalerts.data

import com.alexitc.coinalerts.commons.ApplicationResult
import com.alexitc.coinalerts.errors.{EmailAlreadyExists, UserVerificationTokenNotFound, VerifiedUserNotFound}
import com.alexitc.coinalerts.models._
import org.scalactic.{Bad, Good, One, Or}

import scala.collection.mutable

trait UserInMemoryDataHandler extends UserBlockingDataHandler {

  val userList = mutable.ListBuffer[User]()
  val passwords = mutable.HashMap[UserEmail, UserHiddenPassword]()
  val verificationTokenList = mutable.ListBuffer[(UserId, UserVerificationToken)]()
  val verifiedUserList = mutable.ListBuffer[User]()

  override def create(email: UserEmail, password: UserHiddenPassword): ApplicationResult[User] = {
    if (userList.exists(_.email.string.equalsIgnoreCase(email.string))) {
      Bad(EmailAlreadyExists).accumulating
    } else {
      val newUser = User(UserId.create, email)
      userList += newUser
      passwords += email -> password
      Good(newUser)
    }
  }

  override def createVerificationToken(userId: UserId): ApplicationResult[UserVerificationToken] = {
    val token = UserVerificationToken.create(userId)
    verificationTokenList += userId -> token
    Good(token)
  }

  override def verifyEmail(token: UserVerificationToken): ApplicationResult[User] = {
    val userMaybe = for {
      userId <- verificationTokenList.find(_._2 == token).map(_._1)
      user <- userList.find(_.id == userId)
    } yield {
      verifiedUserList += user
      user
    }

    Or.from(userMaybe, One(UserVerificationTokenNotFound))
  }

  override def getVerifiedUserPassword(email: UserEmail): ApplicationResult[UserHiddenPassword] = {
    val passwordMaybe = passwords.get(email).filter(_ => verifiedUserList.exists(_.email == email))

    Or.from(passwordMaybe, One(VerifiedUserNotFound))
  }

  override def getVerifiedUserByEmail(email: UserEmail): ApplicationResult[User] = {
    val userMaybe = verifiedUserList.find(_.email == email)

    Or.from(userMaybe, One(VerifiedUserNotFound))
  }

  override def getVerifiedUserById(userId: UserId): ApplicationResult[User] = {
    val userMaybe = verifiedUserList.find(_.id == userId)

    Or.from(userMaybe, One(VerifiedUserNotFound))
  }

  override def getUserPreferences(userId: UserId): ApplicationResult[UserPreferences] = {
    Good(UserPreferences.default(userId))
  }
}
