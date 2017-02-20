/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.protocol.amqp.converter.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.protocol.amqp.converter.ProtonMessageConverter;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.utils.IDGenerator;
import org.apache.activemq.artemis.utils.SimpleIDGenerator;
import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.codec.CompositeWritableBuffer;
import org.apache.qpid.proton.codec.DroppingWritableBuffer;
import org.apache.qpid.proton.codec.WritableBuffer;
import org.apache.qpid.proton.message.Message;
import org.apache.qpid.proton.message.ProtonJMessage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * Tests some basic encode / decode functionality on the transformers.
 */
public class MessageTransformationTest {

   @Rule
   public TestName test = new TestName();

   private IDGenerator idGenerator;
   private ProtonMessageConverter converter;

   @Before
   public void setUp() {
      idGenerator = new SimpleIDGenerator(0);
      converter = new ProtonMessageConverter(idGenerator);
   }

   @Test
   public void testEncodeDecodeFidelity() throws Exception {
      Map<String, Object> applicationProperties = new HashMap<>();
      Map<Symbol, Object> messageAnnotations = new HashMap<>();

      applicationProperties.put("property-1", "string");
      applicationProperties.put("property-2", 512);
      applicationProperties.put("property-3", true);

      messageAnnotations.put(Symbol.valueOf("x-opt-jms-msg-type"), 0);
      messageAnnotations.put(Symbol.valueOf("x-opt-jms-dest"), 0);

      Message incomingMessage = Proton.message();

      incomingMessage.setAddress("queue://test-queue");
      incomingMessage.setDeliveryCount(1);
      incomingMessage.setApplicationProperties(new ApplicationProperties(applicationProperties));
      incomingMessage.setMessageAnnotations(new MessageAnnotations(messageAnnotations));
      incomingMessage.setCreationTime(System.currentTimeMillis());
      incomingMessage.setContentType("text/plain");
      incomingMessage.setBody(new AmqpValue("String payload for AMQP message conversion performance testing."));

      EncodedMessage encoded = encode(incomingMessage);

      org.apache.activemq.artemis.api.core.Message outbound = converter.inbound(encoded);
      Message outboudMessage = ((EncodedMessage) converter.outbound(outbound, outbound.getLongProperty("JMSXDeliveryCount").intValue())).decode();

      // Test that message details are equal
      assertEquals(incomingMessage.getAddress(), outboudMessage.getAddress());
      assertEquals(incomingMessage.getDeliveryCount(), outboudMessage.getDeliveryCount());
      assertEquals(incomingMessage.getCreationTime(), outboudMessage.getCreationTime());
      assertEquals(incomingMessage.getContentType(), outboudMessage.getContentType());

      // Test Message annotations
      ApplicationProperties incomingApplicationProperties = incomingMessage.getApplicationProperties();
      ApplicationProperties outgoingApplicationProperties = outboudMessage.getApplicationProperties();

      assertEquals(incomingApplicationProperties.getValue(), outgoingApplicationProperties.getValue());

      // Test Message properties
      MessageAnnotations incomingMessageAnnotations = incomingMessage.getMessageAnnotations();
      MessageAnnotations outgoingMessageAnnotations = outboudMessage.getMessageAnnotations();

      assertEquals(incomingMessageAnnotations.getValue(), outgoingMessageAnnotations.getValue());

      // Test that bodies are equal
      assertTrue(incomingMessage.getBody() instanceof AmqpValue);
      assertTrue(outboudMessage.getBody() instanceof AmqpValue);

      AmqpValue incomingBody = (AmqpValue) incomingMessage.getBody();
      AmqpValue outgoingBody = (AmqpValue) outboudMessage.getBody();

      assertTrue(incomingBody.getValue() instanceof String);
      assertTrue(outgoingBody.getValue() instanceof String);

      assertEquals(incomingBody.getValue(), outgoingBody.getValue());
   }

   @Test
   public void testBodyOnlyEncodeDecode() throws Exception {

      Message incomingMessage = Proton.message();

      incomingMessage.setBody(new AmqpValue("String payload for AMQP message conversion performance testing."));

      org.apache.activemq.artemis.api.core.Message outbound = converter.inbound(new AMQPMessage(incomingMessage, null));
      Message outboudMessage = ((EncodedMessage) converter.outbound(outbound, 1)).decode();

      assertNull(outboudMessage.getHeader());
      assertNull(outboudMessage.getProperties());
   }

   @Test
   public void testPropertiesButNoHeadersEncodeDecode() throws Exception {

      Message incomingMessage = Proton.message();

      incomingMessage.setBody(new AmqpValue("String payload for AMQP message conversion performance testing."));
      incomingMessage.setMessageId("ID:SomeQualifier:0:0:1");

      org.apache.activemq.artemis.api.core.Message outbound = converter.inbound(new AMQPMessage(incomingMessage, null));
      Message outboudMessage = ((EncodedMessage) converter.outbound(outbound, 1)).decode();

      assertNull(outboudMessage.getHeader());
      assertNotNull(outboudMessage.getProperties());
   }

   @Test
   public void testHeaderButNoPropertiesEncodeDecode() throws Exception {

      Message incomingMessage = Proton.message();

      incomingMessage.setBody(new AmqpValue("String payload for AMQP message conversion performance testing."));
      incomingMessage.setDurable(true);

      org.apache.activemq.artemis.api.core.Message outbound = converter.inbound(new AMQPMessage(incomingMessage, null));
      Message outboudMessage = ((EncodedMessage) converter.outbound(outbound, 1)).decode();

      assertNotNull(outboudMessage.getHeader());
      assertNull(outboudMessage.getProperties());
   }

   @Test
   public void testMessageWithAmqpValueThatFailsJMSConversion() throws Exception {

      Message incomingMessage = Proton.message();

      incomingMessage.setBody(new AmqpValue(new Boolean(true)));

      org.apache.activemq.artemis.api.core.Message outbound = converter.inbound(new AMQPMessage(incomingMessage, null));
      Message outboudMessage = ((EncodedMessage) converter.outbound(outbound, 1)).decode();

      Section section = outboudMessage.getBody();
      assertNotNull(section);
      assertTrue(section instanceof AmqpValue);
      AmqpValue amqpValue = (AmqpValue) section;
      assertNotNull(amqpValue.getValue());
      assertTrue(amqpValue.getValue() instanceof Boolean);
      assertEquals(true, amqpValue.getValue());
   }

   @Test
   public void testComplexQpidJMSMessageEncodeDecode() throws Exception {

      Map<String, Object> applicationProperties = new HashMap<>();
      Map<Symbol, Object> messageAnnotations = new HashMap<>();

      applicationProperties.put("property-1", "string-1");
      applicationProperties.put("property-2", 512);
      applicationProperties.put("property-3", true);
      applicationProperties.put("property-4", "string-2");
      applicationProperties.put("property-5", 512);
      applicationProperties.put("property-6", true);
      applicationProperties.put("property-7", "string-3");
      applicationProperties.put("property-8", 512);
      applicationProperties.put("property-9", true);

      messageAnnotations.put(Symbol.valueOf("x-opt-jms-msg-type"), 0);
      messageAnnotations.put(Symbol.valueOf("x-opt-jms-dest"), 0);
      messageAnnotations.put(Symbol.valueOf("x-opt-jms-reply-to"), 0);
      messageAnnotations.put(Symbol.valueOf("x-opt-delivery-delay"), 2000);

      Message message = Proton.message();

      // Header Values
      message.setPriority((short) 9);
      message.setDurable(true);
      message.setDeliveryCount(2);
      message.setTtl(5000);

      // Properties
      message.setMessageId("ID:SomeQualifier:0:0:1");
      message.setGroupId("Group-ID-1");
      message.setGroupSequence(15);
      message.setAddress("queue://test-queue");
      message.setReplyTo("queue://reply-queue");
      message.setCreationTime(System.currentTimeMillis());
      message.setContentType("text/plain");
      message.setCorrelationId("ID:SomeQualifier:0:7:9");
      message.setUserId("username".getBytes(StandardCharsets.UTF_8));

      // Application Properties / Message Annotations / Body
      message.setApplicationProperties(new ApplicationProperties(applicationProperties));
      message.setMessageAnnotations(new MessageAnnotations(messageAnnotations));
      message.setBody(new AmqpValue("String payload for AMQP message conversion performance testing."));

      org.apache.activemq.artemis.api.core.Message outbound = converter.inbound(new AMQPMessage(message, null));
      Message outboudMessage = ((EncodedMessage) converter.outbound(outbound, 1)).decode();

      assertNotNull(outboudMessage.getHeader());
      assertNotNull(outboudMessage.getProperties());
      assertNotNull(outboudMessage.getMessageAnnotations());
      assertNotNull(outboudMessage.getApplicationProperties());
      assertNull(outboudMessage.getDeliveryAnnotations());
      assertNull(outboudMessage.getFooter());

      assertEquals(9, outboudMessage.getApplicationProperties().getValue().size());
      assertEquals(4, outboudMessage.getMessageAnnotations().getValue().size());
   }

   private EncodedMessage encode(Message message) {
      ProtonJMessage amqp = (ProtonJMessage) message;

      ByteBuffer buffer = ByteBuffer.wrap(new byte[1024 * 4]);
      final DroppingWritableBuffer overflow = new DroppingWritableBuffer();
      int c = amqp.encode(new CompositeWritableBuffer(new WritableBuffer.ByteBufferWrapper(buffer), overflow));
      if (overflow.position() > 0) {
         buffer = ByteBuffer.wrap(new byte[1024 * 4 + overflow.position()]);
         c = amqp.encode(new WritableBuffer.ByteBufferWrapper(buffer));
      }

      return new EncodedMessage(1, buffer.array(), 0, c);
   }
}
