package rx.scala

import java.util.concurrent.Callable

import io.{reactivex => rx}
import io.reactivex.functions._

import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

import KTransform._

final class Single[+A](val value: rx.Single[Any]) extends AnyVal { self =>

  @inline def asJava[AA >: A]: rx.Single[AA] = value.asInstanceOf[rx.Single[AA]]

  def map[B](f: A => B): Single[B] =
    asJava[A].map[B](f.convertK[Function]).asScala

  def flatMap[B](f: A => Single[B]): Single[B] =
    asJava[A].flatMap(f.andThen(_.asJava[B]).convertK[Function]).asScala

  def map2[B, C](that: Single[B])(f: (A, B) => C): Single[C] =
    asJava[A].zipWith[B, C](that.asJava[B], f.convertK[BiFunction]).asScala

  def zip[B](that: Single[B]): Single[(A, B)] =
    map2(that)(_ -> _)

  def handle[AA >: A](f: PartialFunction[Throwable, Single[AA]]): Single[AA] = {
    val func = (t: Throwable) => if(f.isDefinedAt(t)) f(t).asJava[AA] else Single.error(t).asJava[AA]
    asJava[AA].onErrorResumeNext(func.convertK[Function]).asScala
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
    asJava[A]
      .subscribe(
          ((a: A) => f(Success(a))).convertK1[Consumer],
          ((t: Throwable) => f(Failure(t))).convertK1[Consumer]
      ).asScala

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

  def error[A](t: => Throwable): Single[A] = rx.Single.error[A]((() => t).convertK[Callable]).asScala

  //For 2.10.6 [[http://stackoverflow.com/questions/21613666/scala-value-class-compilation-fails-for-base-type-with-partial-function-paramete#21614635]]
  private def attempt[A](s: Single[A]): Single[Try[A]] = {
    s.map[Try[A]](Success(_)).handle {
      case t => Single(Failure(t))
    }
  }

  def fromJava[A](s: rx.Single[A]): Single[A] = new Single(s.asInstanceOf[rx.Single[Any]])

  def apply[A](a: A): Single[A] = rx.Single.just(a).asScala
}


