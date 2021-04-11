package fr.janalyse.cem

object Hashes {
  def sha1(that: String): String = {
    // Inspired from https://alvinalexander.com/source-code/scala-method-create-md5-hash-of-string
    val content = if (that ==null) "" else that // TODO - probably discutable, migrate to an effect
    import java.security.MessageDigest
    import java.math.BigInteger
    val md = MessageDigest.getInstance("SHA-1") // TODO - can fail => potential border side effect !
    val digest = md.digest(content.getBytes)
    val bigInt = new BigInteger(1, digest)
    val hashedString = bigInt.toString(16)
    hashedString
  }
}
