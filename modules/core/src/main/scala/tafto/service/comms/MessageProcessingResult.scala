package tafto.service.comms

import tafto.domain.{EmailMessage, EmailStatus}

enum MessageProcessingResult(val id: EmailMessage.Id):
  // message id could not be claimed - Not an error because it could have been claimed by another process
  case CouldNotClaim(override val id: EmailMessage.Id) extends MessageProcessingResult(id)

  // message was sent downstream, and was marked successfully. If `error` is present, there was a downstream error.
  case Marked(override val id: EmailMessage.Id, error: Option[Throwable]) extends MessageProcessingResult(id)

  // message was sent downstream, but was not marked successfully. If `error` is present, there was a downstream error.
  // this case indicates possible duplicate delivery
  case CouldNotMark(override val id: EmailMessage.Id, error: Option[Throwable]) extends MessageProcessingResult(id)

  // message id from claimed messages reprocessing backlog not found. This is an error that indicates a bug in the system.
  case CannotReprocess_NotFound(override val id: EmailMessage.Id) extends MessageProcessingResult(id)

  // message from claimed messages reprocessing backlog no longer has status claimed. This is not an error as it might
  // have been reprocessed by another process
  case CannotReprocess_NoLongerClaimed(override val id: EmailMessage.Id, newStatus: EmailStatus)
    extends MessageProcessingResult(id)
