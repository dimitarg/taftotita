package tafto.rest.api.resources

import tafto.domain.*
import tafto.util.*
import io.circe.generic.semiauto.*
import io.github.iltotore.iron.circe.given
import io.github.iltotore.iron.cats.given

import io.circe.Decoder
import sttp.tapir.Schema
import sttp.tapir.codec.iron.given
import io.circe.Codec

final case class InitSuperAdminRequest(
    email: Email,
    fullName: Option[NonEmptyString],
    password: UserPassword
) derives Schema,
      Codec
