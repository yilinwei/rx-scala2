package rx.scala

import scala.annotation.implicitNotFound

@implicitNotFound("Cannot find conversion from $A to $B")
private[rx] trait Conversion[-A, +B] {
  def apply(a: A): B
}

private[rx] object Conversion extends FunctionConversions

