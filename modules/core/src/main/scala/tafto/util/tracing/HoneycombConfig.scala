package tafto.util.tracing

import ciris.*

final case class HoneycombConfig(
    apiKey: Secret[String],
    serviceName: String,
    globalFields: Map[String, String]
)

object HoneycombConfig:
  def load(serviceName: String, globalFields: Map[String, String]): ConfigValue[Effect, HoneycombConfig] =
    env("HONEYCOMB_API_KEY").secret.map { apiKey =>
      HoneycombConfig(apiKey, serviceName, globalFields)
    }
