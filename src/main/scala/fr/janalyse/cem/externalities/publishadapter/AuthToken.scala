package fr.janalyse.cem.externalities.publishadapter

case class AuthToken(value: String) {
  override def toString: String = value
}
