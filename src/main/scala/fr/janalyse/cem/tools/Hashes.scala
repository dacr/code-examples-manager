/*
 * Copyright 2021 David Crosson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.janalyse.cem.tools

object Hashes {
  def sha1(that: String): String = {
    import java.math.BigInteger
    import java.security.MessageDigest
    // Inspired from https://alvinalexander.com/source-code/scala-method-create-md5-hash-of-string
    val content      = if (that == null) "" else that     // TODO - probably discutable, migrate to an effect
    val md           = MessageDigest.getInstance("SHA-1") // TODO - can fail => potential border side effect !
    val digest       = md.digest(content.getBytes)
    val bigInt       = new BigInteger(1, digest)
    val hashedString = bigInt.toString(16)
    hashedString
  }
}
