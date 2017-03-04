package rx.scala

import cats._

import scala.collection.convert.ImplicitConversionsToJava._
import io.{reactivex => rx}

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

//TODO: Move these to different cross compile
private[rx] final class FunctionOps[A, B](val value: A => B) extends AnyVal {
  def convert: rx.functions.Function[_ >: A, _ <: B] =
    a => value(a)
}

private[rx] final class Function2Ops[A, B, C](val value: (A, B) => C) extends AnyVal {
  def convert: rx.functions.BiFunction[_ >: A, _ >: B, _ <: C] =
    (a, b) => value(a, b)
}

private[rx] object Function {
  implicit def toFunctionsOps[A, B](f: A => B): FunctionOps[A, B] = new FunctionOps(f)
  implicit def toFunction2Ops[A, B, C](f: (A, B) => C): Function2Ops[A, B, C] = new Function2Ops(f)
}

import Function._

private[rx] final class WithFilter[A](p: A => Boolean, o: Observable[A]) {
  def map[B](f: A => B): Observable[B] = o.map(f)
  def flatMap[B, That](f: A => Observable[B]): Observable[B] = o.flatMap(f)
  def foreach(f: A => Unit): Unit = o.foreach(f)
  def withFilter(f: A => Boolean): WithFilter[A] = new WithFilter(a => p(a) && f(a), o)
}

final class Observable[+A](private[rx] val value: rx.Observable[Any]) extends AnyVal { self =>

  def repr[AA >: A]: rx.Observable[AA] = value.asInstanceOf[rx.Observable[AA]]

  /**
    * Example:
    * {{{
    *   scala> import rx.scala._
    *   scala> import scala.concurrent.Await
    *   scala> import scala.concurrent.duration._
    *   scala> val o = Observable(1, 2, 3)
    *   scala> val res = o.filter(_ > 2)
    *   scala> Await.result(res.log, 1 second)
    *   res0: List[Int] = List(3)
    * }}}
    */
  def filter[AA >: A](f: AA => Boolean): Observable[A] =
    repr[A].filter(a => f(a))

  def withFilter[AA >: A](f: AA => Boolean): WithFilter[AA] = new WithFilter(f, self)

  def foreach[AA >: A](f: AA => Unit): Disposable =
    repr[A].forEach(a => f(a))

  /**
    * Example:
    * {{{
    *   scala> import rx.scala._
    *   scala> import scala.concurrent.Await
    *   scala> import scala.concurrent.duration._
    *   scala> val o = Observable("foo", "bar")
    *   scala> val res = o.map(_.length)
    *   scala> Await.result(res.log, 1 second)
    *   res0: List[Int] = List(3, 3)
    * }}}
    */
  def map[B](f: A => B): Observable[B] =
    new Observable[B](repr[A].map(f.convert))

  def mapError(f: PartialFunction[Throwable, Throwable]): Observable[A] =
    handle(f.andThen(t => Observable.error[A](t)))

  def flatMap[B](f: A => Observable[B]): Observable[B] =
    new Observable[B](repr[A].flatMap(f.andThen(_.repr).convert))

  /**
    * Example:
    * {{{
    *   scala> import rx.scala._
    *   scala> import scala.concurrent.Await
    *   scala> import scala.concurrent.duration._
    *   scala> val o = Observable(Observable(1, 2), Observable(3, 4))
    *   scala> val res = o.flatten
    *   scala> Await.result(res.log, 1 second)
    *   res0: List[Int] = List(1, 2, 3, 4)
    * }}}
    */
  def flatten[B](implicit ev: A <:< Observable[B]): Observable[B] = {
    val o = map(_.repr[B]).repr[rx.Observable[B]]
    rx.Observable.merge(o)
  }

  def handle[AA >: A](f: PartialFunction[Throwable, Observable[AA]]): Observable[A] = {
    val func: Throwable => rx.ObservableSource[Any] =
      t => if(f.isDefinedAt(t)) f(t).value else Observable.error(t).value
    new Observable[A](value.onErrorResumeNext(func.convert))
  }

  def handle[AA >: A](f: Throwable => Observable[AA]): Observable[A] =
    new Observable[A](value.onErrorResumeNext(f.andThen(_.value).convert))

  /**
    * Example:
    * {{{
    *   scala> import rx.scala._
    *   scala> import scala.concurrent.Await
    *   scala> import scala.concurrent.duration._
    *   scala> val o1 = Observable.error[Int](new IllegalArgumentException("foo"))
    *   scala> val o2 = Observable(1)
    *   scala> val res = o1.orElse(o2)
    *   scala> Await.result(res.log, 1 second)
    *   res0: List[Int] = List(1)
    * }}}
    */
  def orElse[AA >: A](f: Observable[AA]): Observable[A] =
    new Observable[A](value.onErrorResumeNext(f.value))

  def scan[AA >: A, M](implicit M: Monoid[AA]): Observable[AA] =
    scanLeft(M.empty)(M.combine)

  def scanLeft[AA >: A, B](b: B)(f: (B, AA) => B): Observable[B] =
    repr[AA].scan[B](b, (b, a) => f(b, a))

  def foldLeft[AA >: A, B](b: B)(f: (B, AA) => B): Single[B] =
    repr[AA].reduce[B](b, (b, a) => f(b, a))

  def fold[AA >: A, M](implicit M: Monoid[AA]): Single[AA] =
    repr[AA].reduce((a1, a2) => M.combine(a1, a2)).toSingle(M.empty)

  def debounce(duration: Duration): Observable[A] =
    new Observable[A](value.debounce(duration.length, duration.unit))

  def delay(duration: Duration): Observable[A] =
    new Observable[A](value.delay(duration.length, duration.unit))

  def distinct: Observable[A] =
    new Observable[A](value.distinct())

  def take(count: Long): Observable[A] =
    new Observable[A](value.take(count))

  def take(duration: Duration): Observable[A] =
    new Observable[A](value.take(duration.length, duration.unit))

  def get(index: Long): Single[Option[A]] =
    map(Option(_)).repr.elementAt(index).toSingle(None)

  private def elseDefault[AA >: A](s: Single[Option[AA]], a: => AA): Single[AA] =
    s.map((o: Option[AA]) => o.getOrElse(a))

  def getOrElse[AA >: A](index: Long, a: => AA): Single[AA] =
    elseDefault(get(index), a)

  def head: Single[Option[A]] =
    map(Option(_)).repr.firstElement().toSingle(None)

  def headOrElse[AA >: A](a: => AA): Single[AA] =
    elseDefault(head, a)

  def last: Single[Option[A]] = map(Option(_)).repr.last(None)

  def lastOrElse[AA >: A](a: => AA): Single[AA] =
    elseDefault(last, a)

  def map2[AA >: A, B, C](that: Observable[B])(f: (AA, B) => C): Observable[C] =
    repr[AA].zipWith[B, C](that.repr[B], f.convert)

  def map2Latest[AA >: A, B, C](that: Observable[B])(f: (AA, B) => C): Observable[C] =
    rx.Observable.combineLatest[AA, B, C](self.repr[AA], that.repr[B], f.convert)

  def merge[AA >: A](that: Observable[AA]): Observable[AA] =
    rx.Observable.merge(repr[A], that.repr[AA])

  def zip[B](that: Observable[B]): Observable[(A, B)] = map2(that)(_ -> _)

  def subscribe(): Disposable = value.subscribe()

  def log: Single[List[A]] = foldLeft(List.empty[A])(_ :+ _)

  def onComplete(f: Try[Unit] => Unit): Disposable =
    value.subscribe(_ => (), t => f(Failure(t)), () => f(Success(())))

}


object Observable {

  def empty[A]: Observable[A] = rx.Observable.empty[A]()

  def error[A](t: => Throwable): Observable[A] = new Observable[A](rx.Observable.error(() => t))

  def fromJava[A](o: rx.Observable[A]): Observable[A] = new Observable[A](o.asInstanceOf[rx.Observable[Any]])

  def pure[A](a: A): Observable[A] = rx.Observable.just(a)

  def apply[A](as: A*): Observable[A] =
    as match {
      case Nil => empty
      case x :: Nil => pure(x)
      case _ => rx.Observable.fromIterable(as)
    }

}


