package org.janalyse

import java.io.{BufferedInputStream, ByteArrayInputStream, InputStream}

object Hashes {
  def md5sum(that: String): String = {
    md5sum(new ByteArrayInputStream(that.getBytes())) // TODO : Warning manage charsets...
  }

  def md5sum(input: InputStream): String = {
    val bis = new BufferedInputStream(input)
    val buf = new Array[Byte](1024)
    val md5 = java.security.MessageDigest.getInstance("MD5")
    LazyList.continually(bis.read(buf)).takeWhile(_ != -1).foreach(md5.update(buf, 0, _))
    md5.digest().map(0xFF & _).map {
      "%02x".format(_)
    }.foldLeft("") {
      _ + _
    }
  }

  def murmurHash3(that: String): Int = {
    scala.util.hashing.MurmurHash3.stringHash(that)
  }

  def sha1(that: String): String = {
    // Inspired from https://alvinalexander.com/source-code/scala-method-create-md5-hash-of-string
    import java.security.MessageDigest
    import java.math.BigInteger
    val md = MessageDigest.getInstance("SHA-1")
    val digest = md.digest(that.getBytes)
    val bigInt = new BigInteger(1, digest)
    val hashedString = bigInt.toString(16)
    hashedString
  }
}
