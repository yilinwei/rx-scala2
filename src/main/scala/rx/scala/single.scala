package rx.scala

import java.util.concurrent.Callable

import io.{reactivex => rx}
import io.reactivex.{SingleSource, functions => rxf}

import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

import Conversions._

final class Single[+A](val value: rx.Single[Any]) extends AnyVal { self =>

  def repr[AA >: A]: rx.Single[AA] = value.asInstanceOf[rx.Single[AA]]

  def map[B](f: A => B): Single[B] =
    repr[A].map[B](f.asJava[rxf.Function[A, B]])

  def flatMap[B](f: A => Single[B]): Single[B] =
    repr[A].flatMap(f.andThen(_.repr[B]).asJava[rxf.Function[A, rx.Single[B]]])

  def handle[AA >: A](f: PartialFunction[Throwable, Single[AA]]): Single[AA] = {
    val func = (t: Throwable) => if (f.isDefinedAt(t)) f(t).repr[AA] else Single.error(t).repr[AA]
    repr[AA].onErrorResumeNext(func.asJava[rxf.Function[Throwable, SingleSource[AA]]])
  }

  /**
    * Example:
    * {{{
    *   scala> import rx.scala._
    *   scala> import scala.concurrent.Await
    *   scala> import scala.concurrent.duration._
    *   scala> import scala.util._
    *   scala> val s = Single.error[Int](new IllegalArgumentException("foo"))
    *   scala> val res = s.attempt
    *   scala> Await.result(res, 1 second).isFailure
    *   res0: Boolean = true
    * }}}
    */
  def attempt: Single[Try[A]] = Single.attempt(self)

  def onComplete(f: Try[A] => Unit): Disposable =
    repr[A]
      .subscribe(
          ((a: A) => f(Success(a))).asJava[rxf.Consumer[A]],
          ((t: Throwable) => f(Failure(t))).asJava[rxf.Consumer[Throwable]]
      )

}

private[rx] final class SingleAwaitable[A](s: Single[A]) extends Awaitable[A] { self =>

  @scala.throws[InterruptedException](classOf[InterruptedException])
  @scala.throws[TimeoutException](classOf[TimeoutException])
  override def ready(atMost: Duration)(implicit permit: CanAwait): SingleAwaitable.this.type = {
    val p = Promise[A]
    s.onComplete(p.complete)
    Await.ready(p.future, atMost)
    self
  }

  @scala.throws[Exception](classOf[Exception])
  override def result(atMost: Duration)(implicit permit: CanAwait): A = {
    val p = Promise[A]
    s.onComplete(p.complete)
    Await.result(p.future, atMost)
  }
}

object Single {

  implicit def singleToAwaitable[A](s: Single[A]): Awaitable[A] = new SingleAwaitable[A](s)

  def error[A](t: => Throwable): Single[A] = rx.Single.error[A]((() => t).asJava[Callable[Throwable]])

  //For 2.10.6 [[http://stackoverflow.com/questions/21613666/scala-value-class-compilation-fails-for-base-type-with-partial-function-paramete#21614635]]
  private def attempt[A](s: Single[A]): Single[Try[A]] = {
    s.map[Try[A]](Success(_)).handle {
      case t => Single(Failure(t))
    }
  }

  def fromJava[A](s: rx.Single[A]): Single[A] = new Single(s.asInstanceOf[rx.Single[Any]])

  def apply[A](a: A): Single[A] = rx.Single.just(a)
}


