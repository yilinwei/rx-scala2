package rx.scala

import io.{reactivex => rx}

import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

final class Single[+A](private[rx] val value: rx.Single[Any]) extends AnyVal {
  def repr[AA >: A]: rx.Single[AA] = value.asInstanceOf[rx.Single[AA]]

  def map[AA >: A, B](f: AA => B): Single[B] = ???
  def onComplete[AA >: A](f: Try[AA] => Unit): Disposable =
    repr[AA].subscribe(a => f(Success(a)), t => f(Failure(t)))
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

  def fromJava[A](s: rx.Single[A]): Single[A] = new Single(s.asInstanceOf[rx.Single[Any]])

  def pure[A](a: A): Single[A] = rx.Single.just(a)
}


