/*
 * MIT License
 *
 * Copyright (c) 2020 tommyqqt
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.tommyq.sbe.example;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Test;

import java.util.Base64;

import static org.junit.Assert.*;
import static org.tommyq.sbe.example.SbeHelper.*;

public class SbeExampleTest {
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final NewOrderSingleEncoder nosEncoder = new NewOrderSingleEncoder();

    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final NewOrderSingleDecoder nosDecoder = new NewOrderSingleDecoder();

    private final MutableDirectBuffer buffer = new ExpandableDirectByteBuffer(256);

    @After
    public void tearDown() throws Exception {
        clearBuffer(buffer);
    }

    @Test(expected = java.lang.IndexOutOfBoundsException.class)
    public void testIncorrectEncodedLength() {
        encodeSimpleOrder(nosEncoder, buffer);
        //Incorrect encodedLength!
        final int encodedLength = nosEncoder.encodedLength();
        // final int encodedLength = headerEncoder + nosEncoder.encodedLength();

        final byte[] bytes = new byte[encodedLength];
        buffer.getBytes(0, bytes);
        final DirectBuffer readBuffer = new UnsafeBuffer(bytes);
        wrapDecoder(headerDecoder, nosDecoder, readBuffer, 0);
        System.out.println(nosDecoder);
    }

    @Test
    public void testFindingOutEncodedLengthFromDecoder() {
        encodeSimpleOrder(nosEncoder, buffer);
        final int encodedLength = headerEncoder.encodedLength() + nosEncoder.encodedLength();
        System.out.println(String.format("Message encodedLength=%d", encodedLength));
        wrapDecoder(headerDecoder, nosDecoder, buffer, 0);

        //decoder encoded length at this point = header length + block length
        int encodedLengthFromDecoder = headerDecoder.encodedLength() + nosDecoder.encodedLength();
        System.out.println(String.format("Start of message, decoder encodedLength=%d", encodedLengthFromDecoder));
        assertNotEquals(encodedLengthFromDecoder, encodedLength);

        //Skip to the end
        skipGroup(nosDecoder.allocations(), allocDec -> {
            skipGroup(allocDec.nestedParties(), partyDec -> {
                partyDec.nestedPartyDescription();
            });
            allocDec.allocDescription();
        });
        nosDecoder.traderDescription();
        nosDecoder.orderDescription();

        //decoder encoded length at end of message = actual encoded Length
        encodedLengthFromDecoder = headerDecoder.encodedLength() + nosDecoder.encodedLength();
        System.out.println(String.format("End of message, decoder encodedLength=%d", encodedLengthFromDecoder));
        assertEquals(encodedLength, encodedLengthFromDecoder);
    }

    @Test
    public void testCallingRepeatingGroupMultipleTimes() {
        encodeSimpleOrder(nosEncoder, buffer);
        wrapDecoder(headerDecoder, nosDecoder, buffer, 0);

        NewOrderSingleDecoder.AllocationsDecoder allocDecoder = nosDecoder.allocations();
        System.out.println("Current limit: " + nosDecoder.limit());
        System.out.println("Number of allocations: " + allocDecoder.count()); // count = 2
        System.out.println("Number of allocations: " + nosDecoder.allocations().count()); // count = 20291 !!!
        System.out.println("Current limit: " + nosDecoder.limit());
    }

    @Test
    public void testSporadicIndexOutOfBound () {
        encodeOrder(nosEncoder, "ORDER-001", 20200701,
                "TRADER-001",
                "ORDER DESC",
                buffer);
        //Flip the buffer to decoder
        wrapDecoder(headerDecoder, nosDecoder, buffer, 0);
        System.out.println(nosDecoder);

        /**
         * Encode again with longer traderDescription
         * The length of OrderDescription is corrupted
         * NULL order description
         * Results in ArrayIndexOutOfBound Exception
         */
        encodeOrder(nosEncoder, "ORDER-002", 20200701,
                "TRADER-0001", //Longer trader desc
                null,
                buffer);
        //Flip the buffer to decoder
        wrapDecoder(headerDecoder, nosDecoder, buffer, 0);
        try {
            System.out.println(nosDecoder);
        } catch (Exception e) {
            e.printStackTrace();
        }

        encodeOrder(nosEncoder, "ORDER-002", 20200701,
                "TRADER-001",
                "ORDER DESC",
                buffer);
        //Flip the buffer to decoder
        wrapDecoder(headerDecoder, nosDecoder, buffer, 0);
        System.out.println(nosDecoder);

        /**
         * Encode again with equal length traderDescription
         * The length of OrderDescription stays intact
         * NULL order description
         * The old value of OrderDescription is decoded (Dangerous!)
         */
        encodeOrder(nosEncoder, "ORDER-002", 20200701,
                "TRADER-002", //Longer trader desc
                null,
                buffer);
        //Flip the buffer to decoder
        wrapDecoder(headerDecoder, nosDecoder, buffer, 0);
        System.out.println(nosDecoder);
    }

    @Test
    public void testBufferEncodedAsBase64String() {
        encodeSimpleOrder(nosEncoder, buffer);
        final String encoderToString = nosEncoder.toString();
        System.out.println(encoderToString);

        final int encodedLength = headerEncoder.encodedLength() + nosEncoder.encodedLength();
        final byte[] bytes = new byte[encodedLength];
        buffer.getBytes(0, bytes);

        final String base64EncStr = Base64.getEncoder().encodeToString(bytes);
        System.out.println(base64EncStr);

        final byte[] decoderBytes = Base64.getDecoder().decode(base64EncStr);
        final DirectBuffer decoderBuffer = new UnsafeBuffer(decoderBytes);
        wrapDecoder(headerDecoder, nosDecoder, decoderBuffer, 0);
        final String decoderToString = nosDecoder.toString();
        System.out.println(decoderToString);
        assertEquals(encoderToString, decoderToString);
    }

    @Test
    public void testBacktrackToChangeVarLengthField() {
        wrapEncoder(headerEncoder, nosEncoder, buffer, 0);
        nosEncoder
                .orderId("ORDER-001")
                .tradeDate(20200701);

        final NewOrderSingleEncoder.AllocationsEncoder allocEncoder = nosEncoder.allocationsCount(2);
        encodeAllocation(allocEncoder.next(), "ACCOUNT-1", 100, "Party-1", "Party-1");
        encodeAllocation(allocEncoder.next(), "ACCOUNT-2", 200, "Party-2", "Party-2");

        //I want to change trader description later so remember the limit here
        final int limit = nosEncoder.limit();
        nosEncoder.traderDescription("TRADER-1");
        nosEncoder.orderDescription("ORDER DESC");
        System.out.println(nosEncoder);

        nosEncoder.limit(limit);
        nosEncoder.traderDescription("TRADER-00001");
        //Everything subsequent to the above needs to be encoded again
        nosEncoder.orderDescription("ORDER DESC");
        System.out.println(nosEncoder);
    }

    private void encodeSimpleOrder(final NewOrderSingleEncoder nosEncoder, final MutableDirectBuffer buffer){
        encodeOrder(nosEncoder, "ORDERID-001", 20200701, "TRADER-1", "DUMMY NEW ORDER SINGLE", buffer);
    }

    private void encodeOrder(final NewOrderSingleEncoder nosEncoder,
                             final String orderId,
                             final int tradeDate,
                             final String traderDescription,
                             final String orderDescription,
                             final MutableDirectBuffer buffer){
        wrapEncoder(headerEncoder, nosEncoder, buffer, 0);
        nosEncoder
                .orderId(orderId)
                .tradeDate(tradeDate);

        final NewOrderSingleEncoder.AllocationsEncoder allocEncoder = nosEncoder.allocationsCount(2);
        encodeAllocation(allocEncoder.next(), "ACCOUNT-1", 100, "Party-1", "Party-1");
        encodeAllocation(allocEncoder.next(), "ACCOUNT-2", 200, "Party-2", "Party-2");

        if(traderDescription != null){
            nosEncoder.traderDescription(traderDescription);
        }

        if(orderDescription != null){
            nosEncoder.orderDescription(orderDescription);
        }
    }

    private void encodeAllocation(final NewOrderSingleEncoder.AllocationsEncoder allocEncoder,
                                   final String account,
                                   final double allocQty,
                                   final String partyID,
                                   final String partyDesc){

        allocEncoder.allocAccount(account);
        allocEncoder.allocQty(allocQty);
        final NewOrderSingleEncoder.AllocationsEncoder.NestedPartiesEncoder nestedPartiesEncoder = allocEncoder.nestedPartiesCount(1).next();
        nestedPartiesEncoder.nestedPartyID(partyID);
        nestedPartiesEncoder.nestedPartyDescription(partyDesc);
        allocEncoder.allocDescription("ALLOCATION WITH ACCOUNT " + account);
    }
}