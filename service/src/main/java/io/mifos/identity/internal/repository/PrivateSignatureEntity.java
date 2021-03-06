/*
 * Copyright 2017 The Mifos Initiative.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.mifos.identity.internal.repository;

import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Table;

import java.math.BigInteger;

/**
 * @author Myrle Krantz
 */
@SuppressWarnings("WeakerAccess")
@Table(name = Signatures.TABLE_NAME)
public class PrivateSignatureEntity {
  @PartitionKey
  @Column(name = Signatures.KEY_TIMESTAMP_COLUMN)
  private String keyTimestamp;

  @Column(name = Signatures.VALID_COLUMN)
  private Boolean valid;

  @Column(name = Signatures.PRIVATE_KEY_MOD_COLUMN)
  private BigInteger privateKeyMod;
  @Column(name = Signatures.PRIVATE_KEY_EXP_COLUMN)
  private BigInteger privateKeyExp;

  public String getKeyTimestamp() {
    return keyTimestamp;
  }

  public void setKeyTimestamp(String keyTimestamp) {
    this.keyTimestamp = keyTimestamp;
  }

  public Boolean getValid() {
    return valid;
  }

  public void setValid(Boolean valid) {
    this.valid = valid;
  }

  public BigInteger getPrivateKeyMod() {
    return privateKeyMod;
  }

  public void setPrivateKeyMod(BigInteger privateKeyMod) {
    this.privateKeyMod = privateKeyMod;
  }

  public BigInteger getPrivateKeyExp() {
    return privateKeyExp;
  }

  public void setPrivateKeyExp(BigInteger privateKeyExp) {
    this.privateKeyExp = privateKeyExp;
  }
}
