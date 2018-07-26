/*
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
 */
package org.apache.plc4x.java.base.protocol;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class Plc4XSupportedDataTypes {

    private final static Map<Class, DataTypePair<?>> littleEndianMap;
    private final static Map<Class, DataTypePair<?>> bigEndianMap;

    static {
        Calendar calenderInstance = Calendar.getInstance();
        calenderInstance.setTime(new Date(283686951976960L));
        littleEndianMap = new HashMap<>();
        littleEndianMap.put(Boolean.class, DataTypePair.of(Boolean.TRUE, new byte[]{0x01}));
        littleEndianMap.put(Byte.class, DataTypePair.of(Byte.valueOf("1"), new byte[]{0x1}));
        littleEndianMap.put(Short.class, DataTypePair.of(Short.valueOf("1"), new byte[]{0x1, 0x0}));
        littleEndianMap.put(Float.class, DataTypePair.of(Float.valueOf("1"), new byte[]{0x0, 0x0, (byte) 0x80, 0x3F}));
        littleEndianMap.put(Integer.class, DataTypePair.of(Integer.valueOf("1"), new byte[]{0x1, 0x0, 0x0, 0x0}));
        littleEndianMap.put(Double.class, DataTypePair.of(Double.valueOf("1"), new byte[]{0x0, 0x0, 0x0, 0x0, 0x0, 0x0, (byte) 0xF0, 0x3F}));
        littleEndianMap.put(BigInteger.class, DataTypePair.of(BigInteger.valueOf(1), new byte[]{0x1, 0x0, 0x0, 0x0}));
        littleEndianMap.put(Calendar.class, DataTypePair.of(calenderInstance, new byte[]{0x0, (byte) 0x80, 0x3E, 0x15, (byte) 0xAB, 0x47, (byte) 0xFC, 0x28}));
        littleEndianMap.put(GregorianCalendar.class, littleEndianMap.get(Calendar.class));
        littleEndianMap.put(String.class, DataTypePair.of(String.valueOf("Hello World!"), new byte[]{0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x20, 0x57, 0x6f, 0x72, 0x6c, 0x64, 0x21, 0x00}));
        littleEndianMap.put(byte[].class, DataTypePair.of(new byte[]{0x1, 0x2, 0x3, 0x4}, new byte[]{0x1, 0x2, 0x3, 0x4}));
        littleEndianMap.put(Byte[].class, DataTypePair.of(new Byte[]{0x1, 0x2, 0x3, 0x4}, new byte[]{0x1, 0x2, 0x3, 0x4}));
        bigEndianMap = new HashMap<>();
        littleEndianMap.forEach((clazz, pair) -> {
            Serializable serializable = pair.getValue();
            byte[] littleEndianBytes = pair.getByteRepresentation();
            byte[] bigEndianBytes = ArrayUtils.clone(littleEndianBytes);
            ArrayUtils.reverse(bigEndianBytes);
            bigEndianMap.put(clazz, DataTypePair.of(serializable, bigEndianBytes));
        });
    }

    /**
     * A {@link Stream} of {@link Class}es plc4x can currently support.
     *
     * @return a stream of supported data types.
     */
    public static Stream<Class<? extends Serializable>> streamOfPlc4XSupportedDataTypes() {
        return Stream.of(
            Boolean.class,
            Byte.class,
            Short.class,
            Float.class,
            Integer.class,
            Double.class,
            BigInteger.class,
            Calendar.class,
            String.class,
            byte[].class,
            Byte[].class
        );
    }

    /**
     * A {@link Stream} of instances of {@link Class}es plc4x can currently support with their according little endian byte representation.
     *
     * @return a stream of {@link DataTypePair}s of instances and their byte values.
     * @see #streamOfPlc4XSupportedDataTypes
     */
    public static Stream<? extends DataTypePair<?>> streamOfLittleEndianDataTypePairs() {
        return streamOfLittleEndianDataTypePairs(streamOfPlc4XSupportedDataTypes());
    }

    /**
     * A {@link Stream} of instances of {@link Class}es which are defined by {@code inputStream} can currently support with their according little endian byte representation.
     *
     * @param inputStream a stream of {@link DataTypePair}s of instances and their byte values.
     * @see #streamOfPlc4XSupportedDataTypes
     */
    public static Stream<? extends DataTypePair<?>> streamOfLittleEndianDataTypePairs(Stream<Class<? extends Serializable>> inputStream) {
        return inputStream
            .map(littleEndianMap::get)
            .peek(Objects::requireNonNull);
    }

    /**
     * A {@link Stream} of instances of {@link Class}es plc4x can currently support with their according big endian byte representation.
     *
     * @return a stream of {@link DataTypePair}s of instances and their byte values.
     * @see #streamOfPlc4XSupportedDataTypes
     */
    public static Stream<? extends DataTypePair<?>> streamOfBigEndianDataTypePairs() {
        return streamOfBigEndianDataTypePairs(streamOfPlc4XSupportedDataTypes());
    }

    /**
     * A {@link Stream} of instances of {@link Class}es which are defined by {@code inputStream} can currently support with their according big endian byte representation.
     *
     * @param inputStream a stream of {@link DataTypePair}s of instances and their byte values.
     * @see #streamOfPlc4XSupportedDataTypes
     */
    public static Stream<? extends DataTypePair<?>> streamOfBigEndianDataTypePairs(Stream<Class<? extends Serializable>> inputStream) {
        return inputStream
            .map(bigEndianMap::get)
            .peek(Objects::requireNonNull);
    }

    /**
     * Returns default value for supplied {@code clazz}.
     *
     * @param clazz the default value to get.
     * @param <T>   the type of {@link Class}
     * @return the found default.
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> getDefaultForClass(Class<T> clazz) {
        DataTypePair<?> pair = littleEndianMap.get(clazz);
        if (pair == null) {
            return Optional.empty();
        }
        return Optional.of((T) pair.getValue());
    }

    /**
     * A method which compares a value against a well known default.
     *
     * @param actualValue the value to check.
     */
    public static void defaultAssert(Object actualValue) {
        littleEndianMap.values().forEach(pair -> assertPayloadDependentEquals(actualValue, pair.getValue()));
    }

    private static void assertPayloadDependentEquals(Object actual, Object expected) {
        if (actual.getClass() != expected.getClass()) {
            return;
        }
        assertThat(actual, equalTo(expected));
    }

    /**
     * An wrapper for {@link Pair} that make the usage a bit more readable downstream.
     *
     * @param <T> the type of the contained data type.
     */
    public static class DataTypePair<T extends Serializable> {
        private final Pair<T, byte[]> dataTypePair;

        private DataTypePair(Pair<T, byte[]> dataTypePair) {
            this.dataTypePair = dataTypePair;
        }

        private static <T extends Serializable> DataTypePair<T> of(T value, byte[] bytes) {
            return new DataTypePair<>(ImmutablePair.of(value, bytes));
        }

        /**
         * @return the value of the data type.
         */
        public T getValue() {
            return SerializationUtils.clone(dataTypePair.getLeft());
        }

        /**
         * @return the {@link Class} of the data type.
         */
        public Class<?> getDataTypeClass() {
            return dataTypePair.getLeft().getClass();
        }

        /**
         * @return The byte representation of the data type according to the endianness.
         */
        public byte[] getByteRepresentation() {
            return ArrayUtils.clone(dataTypePair.getRight());
        }

    }
}
