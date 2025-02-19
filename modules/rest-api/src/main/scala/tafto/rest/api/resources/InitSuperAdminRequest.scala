package tafto.rest.api.resources

import io.circe.{Codec, Decoder}
import io.github.iltotore.iron.cats.given
import io.github.iltotore.iron.circe.given
import sttp.tapir.Schema
import sttp.tapir.codec.iron.given
import tafto.domain.*
import tafto.util.*

final case class InitSuperAdminRequest(
    email: Email,
    fullName: Option[NonEmptyString],
    password: UserPassword
) derives Schema,
      Codec
