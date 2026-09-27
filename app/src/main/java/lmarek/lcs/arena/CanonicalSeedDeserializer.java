package lmarek.lcs.arena;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

final class CanonicalSeedDeserializer extends ValueDeserializer<String> {
  @Override
  public String deserialize(JsonParser parser, DeserializationContext context) {
    if (!parser.hasToken(JsonToken.VALUE_STRING)) {
      return (String)
          context.handleUnexpectedToken(
              context.constructType(String.class),
              parser.currentToken(),
              parser,
              "seed must be encoded as a decimal JSON string");
    }
    return parser.getString();
  }
}
