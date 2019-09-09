/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.json.impl;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.core.type.TypeReference;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.json.JsonCodec;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JacksonCodec implements JsonCodec {

  private static final JsonFactory factory = new JsonFactory();

  static {
    // Non-standard JSON but we allow C style comments in our JSON
    factory.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
  }

  @Override
  public <T> T fromString(String json, Class<T> clazz) throws DecodeException {
    return fromParser(createParser(json), clazz);
  }

  public <T> T fromString(String str, TypeReference<T> typeRef) throws DecodeException {
    return fromString(str, classTypeOf(typeRef));
  }

  @Override
  public <T> T fromBuffer(Buffer json, Class<T> clazz) throws DecodeException {
    return fromParser(createParser(json), clazz);
  }

  public <T> T fromBuffer(Buffer buf, TypeReference<T> typeRef) throws DecodeException {
    return fromBuffer(buf, classTypeOf(typeRef));
  }

  @Override
  public <T> T fromValue(Object json, Class<T> toValueType) {
    throw new UnsupportedOperationException("Mapping is not available without Jackson Databind on the classpath");
  }

  public <T> T fromValue(Object json, TypeReference<T> type) {
    throw new UnsupportedOperationException("Mapping is not available without Jackson Databind on the classpath");
  }

  @Override
  public String toString(Object object, boolean pretty) throws EncodeException {
    StringWriter sw = new StringWriter();
    JsonGenerator generator = createGenerator(sw, pretty);
    try {
      encodeJson(object, generator);
      generator.flush();
      return sw.toString();
    } catch (IOException e) {
      throw new EncodeException(e.getMessage(), e);
    } finally {
      close(generator);
    }
  }

  @Override
  public Buffer toBuffer(Object object, boolean pretty) throws EncodeException {
    ByteBuf buf = Unpooled.buffer();
    ByteBufOutputStream out = new ByteBufOutputStream(buf);
    JsonGenerator generator = createGenerator(out, pretty);
    try {
      encodeJson(object, generator);
      generator.flush();
      return Buffer.buffer(buf);
    } catch (IOException e) {
      throw new EncodeException(e.getMessage(), e);
    } finally {
      close(generator);
    }
  }

  private static JsonParser createParser(String str) {
    try {
      return factory.createParser(str);
    } catch (IOException e) {
      throw new DecodeException("Failed to decode:" + e.getMessage(), e);
    }
  }

  private static JsonParser createParser(Buffer buf) {
    try {
      return factory.createParser((InputStream) new ByteBufInputStream(buf.getByteBuf()));
    } catch (IOException e) {
      throw new DecodeException("Failed to decode:" + e.getMessage(), e);
    }
  }

  private static JsonGenerator createGenerator(Writer out, boolean pretty) {
    try {
      JsonGenerator generator = factory.createGenerator(out);
      if (pretty) {
        generator.useDefaultPrettyPrinter();
      }
      return generator;
    } catch (IOException e) {
      throw new DecodeException("Failed to decode:" + e.getMessage(), e);
    }
  }

  private static JsonGenerator createGenerator(OutputStream out, boolean pretty) {
    try {
      JsonGenerator generator = factory.createGenerator(out);
      if (pretty) {
        generator.useDefaultPrettyPrinter();
      }
      return generator;
    } catch (IOException e) {
      throw new DecodeException("Failed to decode:" + e.getMessage(), e);
    }
  }

  public Object fromString(String str) throws DecodeException {
    return fromParser(createParser(str), Object.class);
  }

  public Object fromBuffer(Buffer buf) throws DecodeException {
    return fromParser(createParser(buf), Object.class);
  }

  public static <T> T fromParser(JsonParser parser, Class<T> type) throws DecodeException {
    try {
      parser.nextToken();
      Object res = parseAny(parser);
      return cast(res, type);
    } catch (IOException e) {
      throw new DecodeException(e.getMessage(), e);
    } finally {
      close(parser);
    }
  }

  private static Object parseAny(JsonParser parser) throws IOException, DecodeException {
    switch (parser.getCurrentTokenId()) {
      case JsonTokenId.ID_START_OBJECT:
        return parseObject(parser);
      case JsonTokenId.ID_START_ARRAY:
        return parseArray(parser);
      case JsonTokenId.ID_STRING:
        return parser.getText();
      case JsonTokenId.ID_NUMBER_FLOAT:
      case JsonTokenId.ID_NUMBER_INT:
        return parser.getNumberValue();
      case JsonTokenId.ID_TRUE:
        return Boolean.TRUE;
      case JsonTokenId.ID_FALSE:
         return Boolean.FALSE;
      case JsonTokenId.ID_NULL:
        return null;
      default:
        throw new DecodeException("Unexpected token"/*, parser.getCurrentLocation()*/);
    }
  }

  private static Map<String, Object> parseObject(JsonParser parser) throws IOException {
    String key1 = parser.nextFieldName();
    if (key1 == null) {
      return new LinkedHashMap<>(2);
    }
    parser.nextToken();
    Object value1 = parseAny(parser);
    String key2 = parser.nextFieldName();
    if (key2 == null) {
      LinkedHashMap<String, Object> obj = new LinkedHashMap<>(2);
      obj.put(key1, value1);
      return obj;
    }
    parser.nextToken();
    Object value2 = parseAny(parser);
    String key = parser.nextFieldName();
    if (key == null) {
      LinkedHashMap<String, Object> obj = new LinkedHashMap<>(2);
      obj.put(key1, value1);
      obj.put(key2, value2);
      return obj;
    }
    // General case
    LinkedHashMap<String, Object> obj = new LinkedHashMap<>();
    obj.put(key1, value1);
    obj.put(key2, value2);
    do {
      parser.nextToken();
      Object value = parseAny(parser);
      obj.put(key, value);
      key = parser.nextFieldName();
    } while (key != null);
    return obj;
  }

  private static List<Object> parseArray(JsonParser parser) throws IOException {
    List<Object> array = new ArrayList<>();
    while (true) {
      parser.nextToken();
      int tokenId = parser.getCurrentTokenId();
      if (tokenId == JsonTokenId.ID_FIELD_NAME) {
        throw new UnsupportedOperationException();
      } else if (tokenId == JsonTokenId.ID_END_ARRAY) {
        return array;
      }
      Object value = parseAny(parser);
      array.add(value);
    }
  }

  static void close(Closeable parser) {
    try {
      parser.close();
    } catch (IOException ignore) {
    }
  }

  // In recursive calls, the callee is in charge of opening and closing the data structure
  private static void encodeJson(Object json, JsonGenerator generator) throws EncodeException {
    try {
      if (json instanceof JsonObject) {
        json = ((JsonObject)json).getMap();
      } else if (json instanceof JsonArray) {
        json = ((JsonArray)json).getList();
      }
      if (json instanceof Map) {
        generator.writeStartObject();
        for (Map.Entry<String, ?> e : ((Map<String, ?>)json).entrySet()) {
          generator.writeFieldName(e.getKey());
          encodeJson(e.getValue(), generator);
        }
        generator.writeEndObject();
      } else if (json instanceof List) {
        generator.writeStartArray();
        for (Object item : (List)json) {
          encodeJson(item, generator);
        }
        generator.writeEndArray();
      } else if (json instanceof String) {
        generator.writeString((String)json);
      } else if (json instanceof Number) {
        if (json instanceof Short) {
          generator.writeNumber((Short) json);
        } else if (json instanceof Integer) {
          generator.writeNumber((Integer) json);
        } else if (json instanceof Long) {
          generator.writeNumber((Long) json);
        } else if (json instanceof Float) {
          generator.writeNumber((Float) json);
        } else if (json instanceof Double) {
          generator.writeNumber((Double) json);
        } else {
          throw new UnsupportedOperationException();
        }
      } else if (json instanceof Boolean) {
        generator.writeBoolean((Boolean)json);
      } else if (json instanceof Instant) {
        generator.writeString((ISO_INSTANT.format((Instant)json)));
      } else if (json instanceof byte[]) {
        generator.writeString(Base64.getEncoder().encodeToString((byte[]) json));
      } else if (json == null) {
        generator.writeNull();
      } else {
        throw new UnsupportedOperationException();
      }
    } catch (IOException e) {
      throw new EncodeException(e.getMessage(), e);
    }
  }

  private static <T> Class<T> classTypeOf(TypeReference<T> typeRef) {
    Type type = typeRef.getType();
    if (type instanceof Class) {
      return (Class<T>) type;
    } else if (type instanceof ParameterizedType) {
      return (Class<T>) ((ParameterizedType)type).getRawType();
    } else {
      throw new DecodeException();
    }
  }

  private static <T> T cast(Object o, Class<T> clazz) {
    if (o instanceof Map) {
      if (!clazz.isAssignableFrom(Map.class)) {
        throw new DecodeException("Failed to decode");
      }
      if (clazz == Object.class) {
        o = new JsonObject((Map) o);
      }
      return clazz.cast(o);
    } else if (o instanceof List) {
      if (!clazz.isAssignableFrom(List.class)) {
        throw new DecodeException("Failed to decode");
      }
      if (clazz == Object.class) {
        o = new JsonArray((List) o);
      }
      return clazz.cast(o);
    } else if (o instanceof String) {
      if (!clazz.isAssignableFrom(String.class)) {
        throw new DecodeException("Failed to decode");
      }
      return clazz.cast(o);
    } else if (o instanceof Boolean) {
      if (!clazz.isAssignableFrom(Boolean.class)) {
        throw new DecodeException("Failed to decode");
      }
      return clazz.cast(o);
    } else if (o == null) {
      return null;
    } else {
      Number number = (Number) o;
      if (clazz == Integer.class) {
        o = number.intValue();
      } else if (clazz == Long.class) {
        o = number.longValue();
      } else if (clazz == Float.class) {
        o = number.floatValue();
      } else if (clazz == Double.class) {
        o = number.doubleValue();
      } else if (clazz == Byte.class) {
        o = number.byteValue();
      } else if (clazz == Short.class) {
        o = number.shortValue();
      } else if (clazz == Object.class || clazz.isAssignableFrom(Number.class)) {
        // Nothing
      } else {
        throw new DecodeException("Failed to decode");
      }
      return clazz.cast(o);
    }
  }
}