package tafto.persist

import skunk.Fragment

object unsafe:
  extension [A](fragment: Fragment[A])
    def unsafeInterpolate(input: A)(
        shouldQuote: List[Boolean],
        quote: String => String = x => s"'${x}'"
    ): Fragment[skunk.Void] =
      val placeholders = fragment.encoder.sql.runA(1).value.split(",").map(_.trim()).toList
      val encodedParams = fragment.encoder.encode(input)

      if shouldQuote.length != placeholders.length then
        sys.error("fatal error: unsafeInterpolate shouldQuote size mismatch")

      val resultSql = placeholders.zip(shouldQuote).zip(encodedParams).foldRight(fragment.sql) {
        case (((placeholder, shouldQuote), param), sql) =>
          val replacement = param match
            case None                   => "null"
            case Some(x) if shouldQuote => quote(x.unredact.value)
            case Some(x)                => x.unredact.value
          sql.replace(placeholder, replacement)

      }

      Fragment(List(Left(resultSql)), skunk.Void.codec, fragment.origin)
