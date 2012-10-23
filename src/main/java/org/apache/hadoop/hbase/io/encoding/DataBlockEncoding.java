/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.apache.hadoop.hbase.io.encoding;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.hbase.util.Bytes;

/**
 * Provide access to all data block encoding algorithms. All of the algorithms
 * are required to have unique id which should <b>NEVER</b> be changed. If you
 * want to add a new algorithm/version, assign it a new id. Announce the new id
 * in the HBase mailing list to prevent collisions.
 */
public enum DataBlockEncoding {

  /** Disable data block encoding. */
  NONE(0, new CopyKeyDataBlockEncoder()) {
    @Override
    public int inBlockMetadataSize() {
      return 0; 
    }

    @Override
    public int encodingIdSize() {
      return 0;
    }
  },

  // id 1 is reserved for the BITSET algorithm to be added later
  PREFIX(2, new PrefixKeyDeltaEncoder()),
  DIFF(3, new DiffKeyDeltaEncoder()),
  FAST_DIFF(4, new FastDiffDeltaEncoder());

  private final short id;
  private final byte[] idInBytes;
  private final DataBlockEncoder encoder;

  public static final int ID_SIZE = Bytes.SIZEOF_SHORT;

  /** Maps data block encoding ids to enum instances. */
  private static Map<Short, DataBlockEncoding> idToEncoding =
      new HashMap<Short, DataBlockEncoding>();

  static {
    for (DataBlockEncoding algo : values()) {
      if (idToEncoding.containsKey(algo.id)) {
        throw new RuntimeException(String.format(
            "Two data block encoder algorithms '%s' and '%s' have " +
            "the same id %d",
            idToEncoding.get(algo.id).toString(), algo.toString(),
            (int) algo.id));
      }
      idToEncoding.put(algo.id, algo);
    }
  }

  private DataBlockEncoding(int id, DataBlockEncoder encoder) {
    if (id < Short.MIN_VALUE || id > Short.MAX_VALUE) {
      throw new AssertionError(
          "Data block encoding algorithm id is out of range: " + id);
    }
    this.id = (short) id;
    this.idInBytes = Bytes.toBytes(this.id);
    if (idInBytes.length != ID_SIZE) {
      // White this may seem redundant, if we accidentally serialize
      // the id as e.g. an int instead of a short, all encoders will break.
      throw new RuntimeException("Unexpected length of encoder ID byte " +
          "representation: " + Bytes.toStringBinary(idInBytes));
    }
    this.encoder = encoder;
  }

  /**
   * @return name converted to bytes.
   */
  public byte[] getNameInBytes() {
    return Bytes.toBytes(toString());
  }

  /**
   * @return The id of a data block encoder.
   */
  public short getId() {
    return id;
  }

  /**
   * Writes id in bytes.
   * @param stream where the id should be written.
   */
  public void writeIdInBytes(OutputStream stream) throws IOException {
    stream.write(idInBytes);
  }

  /**
   * Return new data block encoder for given algorithm type.
   * @return data block encoder if algorithm is specified, null if none is
   *         selected.
   */
  public DataBlockEncoder getEncoder() {
    return encoder;
  }

  /**
   * Provide access to all data block encoders, even those which are not
   * exposed in the enum. Useful for testing and benchmarking.
   * @return list of all data block encoders.
   */
  public static List<DataBlockEncoder> getAllEncoders() {
    ArrayList<DataBlockEncoder> encoders = new ArrayList<DataBlockEncoder>();
    for (DataBlockEncoding algo : values()) {
      DataBlockEncoder encoder = algo.getEncoder();
      if (encoder != null) {
        encoders.add(encoder);
      }
    }

    // Add encoders that are only used in testing.
    encoders.add(new CopyKeyDataBlockEncoder());
    return encoders;
  }

  /**
   * Find and create data block encoder for given id;
   * @param encoderId id of data block encoder.
   * @return Newly created data block encoder.
   */
  public static DataBlockEncoder getDataBlockEncoderById(short encoderId) {
    if (!idToEncoding.containsKey(encoderId)) {
      throw new IllegalArgumentException(String.format(
          "There is no data block encoder for given id '%d'",
          (int) encoderId));
    }

    return idToEncoding.get(encoderId).getEncoder();
  }

  /**
   * Find and return the name of data block encoder for the given id.
   * @param encoderId id of data block encoder
   * @return name, same as used in options in column family
   */
  public static String getNameFromId(short encoderId) {
    return idToEncoding.get(encoderId).toString();
  }

  /**
   * Check if given encoder has this id.
   * @param encoder encoder which id will be checked
   * @param encoderId id which we except
   * @return true if id is right for given encoder, false otherwise
   * @exception IllegalArgumentException
   *            thrown when there is no matching data block encoder
   */
  public static boolean isCorrectEncoder(DataBlockEncoder encoder,
      short encoderId) {
    if (!idToEncoding.containsKey(encoderId)) {
      throw new IllegalArgumentException(String.format(
          "There is no data block encoder for given id '%d'",
          (int) encoderId));
    }

    DataBlockEncoding algorithm = idToEncoding.get(encoderId);
    return algorithm.getClass().equals(encoder.getClass());
  }

  public static DataBlockEncoding getEncodingById(short dataBlockEncodingId) {
    return idToEncoding.get(dataBlockEncodingId);
  }

  /**
   * Size of metadata inside the encoded block. This metadata is not considered part of the block
   * header, but is part of the block payload. It does not include the encoding id.
   */
  public int inBlockMetadataSize() {
    return Bytes.SIZEOF_INT;  // unencoded size
  }

  public int encodingIdSize() {
    return ID_SIZE;
  }

}
