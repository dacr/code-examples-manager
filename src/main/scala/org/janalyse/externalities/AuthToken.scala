package org.janalyse.externalities

case class AuthToken(value: String) {
  override def toString: String = value
}
