package rx.scala

import java.util.concurrent.Callable

import scala.annotation.implicitNotFound
import io.reactivex.{functions => rxf}

@implicitNotFound("Cannot find conversion from $A to $B")
private[rx] trait Conversion[-A, +B] {
  def apply(a: A): B
}

trait FunctionConversions {

  implicit def functionConversion[A, B]: Conversion[A => B, rxf.Function[A, B]] =
    new Conversion[A => B, rxf.Function[A, B]] {
      override def apply(f: A => B): rxf.Function[A, B] =
        new rxf.Function[A, B] {
          override def apply(a: A): B = f(a)
        }
    }

  implicit def predicateConversion[A]: Conversion[A => Boolean, rxf.Predicate[A]] =
    new Conversion[A => Boolean, rxf.Predicate[A]] {
      override def apply(f: A => Boolean): rxf.Predicate[A] =
        new rxf.Predicate[A] {
          override def test(a: A): Boolean = f(a)
        }
    }

  implicit def consumerConversion[A]: Conversion[A => Unit, rxf.Consumer[A]] =
    new Conversion[A => Unit, rxf.Consumer[A]] {
      override def apply(f: A => Unit): rxf.Consumer[A] =
        new rxf.Consumer[A] {
          override def accept(a: A): Unit = f(a)
        }
    }

  implicit def function2Conversion[A, B, C]: Conversion[(A, B) => C, rxf.BiFunction[A, B, C]] =
    new Conversion[(A, B) => C, rxf.BiFunction[A, B, C]] {
      override def apply(f: (A, B) => C): rxf.BiFunction[A, B, C] =
        new rxf.BiFunction[A, B, C] {
          override def apply(a: A, b: B): C = f(a, b)
        }
    }

  implicit def actionConversion[A]: Conversion[() => A, rxf.Action] =
    new Conversion[() => A, rxf.Action] {
      override def apply(f: () => A): rxf.Action =
        new rxf.Action {
          override def run(): Unit = f()
        }
    }

  implicit def callableConversion[A]: Conversion[() => A, Callable[A]] =
    new Conversion[() => A, Callable[A]] {
      override def apply(f: () => A): Callable[A] =
        new Callable[A] {
          override def call(): A = f()
        }
    }
}

private[rx] final class Function0Ops[A](val value: () => A) extends AnyVal { self =>
  def asJava[O](implicit conversion: Conversion[() => A, O]): O = conversion(value)
}

private[rx] final class FunctionOps[A, B](val value: A => B) extends AnyVal { self =>
  def asJava[O](implicit conversion: Conversion[A => B, O]): O = conversion(value)
}

private[rx] final class Function2Ops[A, B, C](val value: (A, B) => C) extends AnyVal {
  def asJava[O](implicit conversion: Conversion[(A, B) => C, O]): O = conversion(value)
}

private[rx] object Conversions extends FunctionConversions {
  implicit def toFunction0Ops[A](f: () => A): Function0Ops[A] = new Function0Ops(f)
  implicit def toFunction1Ops[A, B](f: A => B): FunctionOps[A, B] = new FunctionOps(f)
  implicit def toFunction2Ops[A, B, C](f: (A, B) => C): Function2Ops[A, B, C] = new Function2Ops(f)

}
