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
import org.agrona.MutableDirectBuffer;
import org.agrona.sbe.MessageDecoderFlyweight;
import org.agrona.sbe.MessageEncoderFlyweight;

import java.util.Iterator;
import java.util.function.Consumer;

public class SbeHelper {

    public static void wrapEncoder(final MessageHeaderEncoder headerEncoder,
                                    final MessageEncoderFlyweight encoder,
                                    final MutableDirectBuffer buffer,
                                    final int offset){
        headerEncoder
                .wrap(buffer, offset)
                .blockLength(encoder.sbeBlockLength())
                .templateId(encoder.sbeTemplateId())
                .schemaId(encoder.sbeSchemaId())
                .version(encoder.sbeSchemaVersion());

        encoder.wrap(buffer, offset + headerEncoder.encodedLength());
    }

    public static void wrapDecoder(final MessageHeaderDecoder headerDecoder,
                                   final MessageDecoderFlyweight decoder,
                                   final DirectBuffer buffer,
                                   final int offset){
        headerDecoder.wrap(buffer, offset);
        decoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());
    }

    public static <T extends Iterator<T>> void skipGroup(final T decoder) {
        skipGroup(decoder,  dec -> {});
    }

    public static <T extends Iterator<T>> void skipGroup(final T decoder, Consumer<T> consumer){
        while(decoder.hasNext()){
            decoder.next();
            consumer.accept(decoder);
        }
    }

    public static void clearBuffer(final MutableDirectBuffer buffer){
        buffer.setMemory(0, buffer.capacity(), (byte)0);
    }

}
