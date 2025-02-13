package tafto.itest.util

import skunk.codec.all.*
import skunk.implicits.*
import tafto.persist.codecs.{emailMessageId, emailStatus}

object TestQueries:
  val updateMessageTimestamps = sql"""
    update email_messages set created_at = ${timestamptz}, updated_at = ${timestamptz.opt} where id = ${emailMessageId}
  """.command

  val updateMessageStatus = sql"""
    update email_messages set status = ${emailStatus} where id = ${emailMessageId}
  """.command

  def getMessageStatuses(size: Int) = sql"""
   select id, status from email_messages where id in (${emailMessageId.list(size)})
  """.query(emailMessageId ~ emailStatus)

  def deleteAllMessages = sql"""
    delete from email_messages;
  """.command
