package step.grid.agent;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import step.grid.io.stream.JsonMessage;

// This is the actual codec for the JsonMessage class used for Websocket messages. See comments in that class too.
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY)
public class JsonMessageCodec implements JsonMessage.Codec {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            // Mixins are great - this will apply the JsonTypeInfo annotation of this class to the JsonMessage class!
            .addMixIn(JsonMessage.class, JsonMessageCodec.class)
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final ObjectReader READER = MAPPER.readerFor(JsonMessage.class);
    private static final ObjectWriter WRITER = MAPPER.writerFor(JsonMessage.class);


    @Override
    public String toString(JsonMessage jsonMessage) {
        try {
            return WRITER.writeValueAsString(jsonMessage);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public  <T extends JsonMessage> T fromString(String json) {
        try {
            return READER.readValue(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
