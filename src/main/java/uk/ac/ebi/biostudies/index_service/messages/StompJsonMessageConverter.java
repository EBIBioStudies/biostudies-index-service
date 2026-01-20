package uk.ac.ebi.biostudies.index_service.messages;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.util.MimeType;

import java.nio.charset.StandardCharsets;

/**
 * Custom JSON message converter for STOMP messaging using Jackson ObjectMapper.
 * This replaces the deprecated MappingJackson2MessageConverter.
 */
public class StompJsonMessageConverter extends AbstractMessageConverter {

  private final ObjectMapper objectMapper;

  public StompJsonMessageConverter() {
    super(new MimeType("application", "json", StandardCharsets.UTF_8));
    this.objectMapper = new ObjectMapper();
  }

  public StompJsonMessageConverter(ObjectMapper objectMapper) {
    super(new MimeType("application", "json", StandardCharsets.UTF_8));
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean supports(Class<?> clazz) {
    return true;
  }

  @Override
  protected Object convertFromInternal(Message<?> message, Class<?> targetClass, Object conversionHint) {
    try {
      Object payload = message.getPayload();
      if (payload instanceof byte[]) {
        return objectMapper.readValue((byte[]) payload, targetClass);
      } else if (payload instanceof String) {
        return objectMapper.readValue((String) payload, targetClass);
      }
      throw new MessageConversionException("Unsupported payload type: " + payload.getClass());
    } catch (Exception e) {
      throw new MessageConversionException("Failed to convert from JSON", e);
    }
  }

  @Override
  protected Object convertToInternal(Object payload, MessageHeaders headers, Object conversionHint) {
    try {
      return objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new MessageConversionException("Failed to convert to JSON", e);
    }
  }
}
