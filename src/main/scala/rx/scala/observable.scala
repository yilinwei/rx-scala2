package rx.scala

import java.util.concurrent.Callable

import cats._

import scala.collection.JavaConverters._
import io.{reactivex => rx}
import io.reactivex.{functions => rxf}

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}
import Conversions._

private[rx] final class WithFilter[+A](p: A => Boolean, o: Observable[A]) {
  def map[B](f: A => B): Observable[B] = o.map(f)
  def flatMap[B, That](f: A => Observable[B]): Observable[B] = o.flatMap(f)
  def foreach(f: A => Unit): Unit = o.foreach(f)
  def withFilter(f: A => Boolean): WithFilter[A] = new WithFilter[A](a => p(a) && f(a), o)
}

final class Observable[+A](val value: rx.Observable[Any]) extends AnyVal { self =>

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
  def filter(f: A => Boolean): Observable[A] =
    repr[A].filter(f.asJava[rxf.Predicate[A]])

  def withFilter(f: A => Boolean): WithFilter[A] = new WithFilter(f, self)

  def foreach(f: A => Unit): Disposable =
    repr[A].forEach(f.asJava[rxf.Consumer[A]])

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
    repr[A].map[B](f.asJava[rxf.Function[A, B]])

  def mapError(f: PartialFunction[Throwable, Throwable]): Observable[A] =
    handle(f.andThen(t => Observable.error[A](t)))

  /**
    * Example:
    * {{{
    *   scala> import rx.scala._
    *   scala> import scala.concurrent.Await
    *   scala> import scala.concurrent.duration._
    *   scala> val o = Observable(1, 2)
    *   scala> val res = o.flatMap(a => Observable(a + 1, a + 2))
    *   scala> Await.result(res.log, 1 second)
    *   res0: List[Int] = List(2, 3, 3, 4)
    * }}}
    */
  def flatMap[B](f: A => Observable[B]): Observable[B] =
    repr[A].flatMap(f.andThen(_.repr[B]).asJava[rxf.Function[A, rx.Observable[B]]])

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

  /**
    * Example:
    * {{{
    *   scala> import rx.scala._
    *   scala> import scala.concurrent.Await
    *   scala> import scala.concurrent.duration._
    *   scala> val o = Observable.error[Int](new IllegalArgumentException("foo"))
    *   scala> val res = o.handle { case t: IllegalArgumentException => Observable(1) }
    *   scala> Await.result(res.log, 1 second)
    *   res0: List[Int] = List(1)
    * }}}
    */
  def handle[AA >: A](f: PartialFunction[Throwable, Observable[AA]]): Observable[AA] = {
    val func: Throwable => rx.ObservableSource[AA] =
      t => if(f.isDefinedAt(t)) f(t).repr[AA] else Observable.error[A](t).repr[AA]
    repr[AA].onErrorResumeNext(func.asJava[rxf.Function[Throwable, rx.ObservableSource[AA]]])
  }

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
  def orElse[AA >: A](that: Observable[AA]): Observable[AA] =
    repr[AA].onErrorResumeNext(that.repr[AA])

  //TODO: move out into subproject
  def scan[AA >: A, M](implicit M: Monoid[AA]): Observable[AA] =
    scanLeft(M.empty)(M.combine)

  /**
    * Example:
    * {{{
    *   scala> import rx.scala._
    *   scala> import scala.concurrent.Await
    *   scala> import scala.concurrent.duration._
    *   scala> val o = Observable(1, 2, 3, 4, 5)
    *   scala> val res = o.scanLeft(0)(_ + _)
    *   scala> Await.result(res.log, 1 second)
    *   res0: List[Int] = List(0, 1, 3, 6, 10, 15)
    * }}}
    */
  def scanLeft[B](b: B)(f: (B, A) => B): Observable[B] =
    repr[A].scan[B](b, f.asJava[rxf.BiFunction[B, A, B]])

  /**
    * Example:
    * {{{
    *   scala> import rx.scala._
    *   scala> import scala.concurrent.Await
    *   scala> import scala.concurrent.duration._
    *   scala> val o = Observable(1, 2, 3)
    *   scala> val res = o.foldLeft(10)(_ - _)
    *   scala> Await.result(res, 1 second)
    *   res0: Int = 4
    * }}}
    */
  def foldLeft[B](b: B)(f: (B, A) => B): Single[B] =
    repr[A].reduce[B](b, f.asJava[rxf.BiFunction[B, A, B]])

  //TODO: move out into subproject
  def fold[AA >: A, M](implicit M: Monoid[AA]): Single[AA] =
    repr[AA].reduce((M.combine _).asJava[rxf.BiFunction[AA, AA, AA]]).toSingle(M.empty)

  def debounce(duration: Duration): Observable[A] =
    repr.debounce(duration.length, duration.unit)

  def delay(duration: Duration): Observable[A] =
    repr[A].delay(duration.length, duration.unit)

  /**
    * Example:
    * {{{
    *   scala> import rx.scala._
    *   scala> import scala.concurrent.Await
    *   scala> import scala.concurrent.duration._
    *   scala> val o = Observable(1, 1, 3, 4)
    *   scala> val res = o.distinct
    *   scala> Await.result(res.log, 1 second)
    *   res0: List[Int] = List(1, 3, 4)
    * }}}
    */
  def distinct: Observable[A] =
    repr[A].distinct

  def drop(count: Long): Observable[A] =
    repr[A].skip(count)

  def dropWhile(f: A => Boolean): Observable[A] =
    repr[A].skipWhile(f.asJava[rxf.Predicate[A]])

  def tail: Observable[A] = drop(1)

  def take(count: Long): Observable[A] =
    repr[A].take(count)

  def take(duration: Duration): Observable[A] =
    repr[A].take(duration.length, duration.unit)

  def takeWhile(f: A => Boolean): Observable[A] =
    repr[A].takeWhile(f.asJava[rxf.Predicate[A]])

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

  def map2[B, C](that: Observable[B])(f: (A, B) => C): Observable[C] =
    repr[A].zipWith[B, C](that.repr[B], f.asJava[rxf.BiFunction[A, B, C]])

  def map2Latest[B, C](that: Observable[B])(f: (A, B) => C): Observable[C] =
    rx.Observable.combineLatest[A, B, C](self.repr[A], that.repr[B], f.asJava[rxf.BiFunction[A, B, C]])

  def merge[AA >: A](that: Observable[AA]): Observable[AA] =
    rx.Observable.merge(repr[A], that.repr[AA])

  def zip[B](that: Observable[B]): Observable[(A, B)] = map2(that)(_ -> _)

  def subscribe(): Disposable = value.subscribe()

  def log: Single[List[A]] = {
    foldLeft(List.empty[A])((b, a) => a :: b).map(_.reverse)
  }

  /** @see [[++]] */
  def concat[AA >: A](that: Observable[AA]): Observable[AA] =
    repr[AA].concatWith(that.repr)

  /**
    * Example:
    * {{{
    *   scala> import rx.scala._
    *   scala> import scala.concurrent.Await
    *   scala> import scala.concurrent.duration._
    *   scala> val o1 = Observable(1, 2)
    *   scala> val o2 = Observable(3, 4)
    *   scala> val res = o1 ++ o2
    *   scala> Await.result(res.log, 1 second)
    *   res0 = List(1, 2, 3, 4)
    * }}}
    *
    */
  def ++[AA >: A](that: Observable[AA]): Observable[AA] =
    concat(that)

  def onComplete(f: Try[Unit] => Unit): Disposable =
    repr[A].subscribe(
      ((_: A) => ()).asJava[rxf.Consumer[A]],
      ((t: Throwable) => f(Failure(t))).asJava[rxf.Consumer[Throwable]],
      (() => f(Success(()))).asJava[rxf.Action]
    )

}


object Observable {

  def empty[A]: Observable[A] = rx.Observable.empty[A]()

  def error[A](t: => Throwable): Observable[A] = new Observable[A](rx.Observable.error((() => t).asJava[Callable[Throwable]]))

  def fromJava[A](o: rx.Observable[A]): Observable[A] = new Observable[A](o.asInstanceOf[rx.Observable[Any]])

  def suspend[A](a: => Observable[A]): Observable[A] = rx.Observable.defer((() => a.repr[A]).asJava[Callable[rx.Observable[A]]])

  /**
    * Example:
    * {{{
    *   scala> import rx.scala._
    *   scala> import scala.concurrent.Await
    *   scala> import scala.concurrent.duration._
    *   scala> val o = Observable.iterate(0)(_ + 1)
    *   scala> val res = o.take(4)
    *   scala> Await.result(res.log, 1 second)
    *   res0: List[Int] = List(0, 1, 2, 3)
    * }}}
    */
  def iterate[A](a: A)(f: A => A): Observable[A] =
    pure(a) ++ rx.Observable
      .generate[A, A](
        (() => a).asJava[Callable[A]],
        ((a: A, emitter: rx.Emitter[A]) => {
          val next = f(a)
          emitter.onNext(next)
          next
        }).asJava[rxf.BiFunction[A, rx.Emitter[A], A]]
    )

  /**
    * Example:
    * {{{
    *   scala> import rx.scala._
    *   scala> import scala.concurrent.Await
    *   scala> import scala.concurrent.duration._
    *   scala> val res = Observable.iterateWhile(0)(a => if(a < 3) Some(a + 1) else None)
    *   scala> Await.result(res.log, 1 second)
    *   res0: List[Int] = List(0, 1, 2, 3)
    * }}}
    */
  def iterateWhile[A](a: A)(f: A => Option[A]): Observable[A] = {
    pure(a) ++ rx.Observable.generate(
      (() => Some(a)).asJava[Callable[Option[A]]],
      ((a: Option[A], emitter: rx.Emitter[A]) => {
        a.map(f).flatMap {
          case None =>
            emitter.onComplete()
            None
          case next @ Some(value) =>
            emitter.onNext(value)
            next
      }
    }).asJava[rxf.BiFunction[Option[A], rx.Emitter[A], Option[A]]])
  }

  /**
    * Example:
    * {{{
    *   scala> import rx.scala._
    *   scala> import scala.concurrent.Await
    *   scala> import scala.concurrent.duration._
    *   scala> val res = Observable.range(0, 3)
    *   res0: List[Int] = List(0, 1, 2, 3)
    * }}}
    */
  def range(start: Int, end: Int): Observable[Int] = {
    require(end > start)
    if(start == end) empty else fromJava(rx.Observable.range(start, end - start)).map(_.toInt)
  }

  /**
    * Example:
    * {{{
    *   scala> import rx.scala._
    *   scala> import scala.concurrent.Await
    *   scala> import scala.concurrent.duration._
    *   scala> val interval = Observable.interval(100 milliseconds)
    *   scala> val res = interval.take(150 milliseconds)
    *   scala> Await.result(res.log, 1 second).size
    *   res0: Int = 1
    * }}}
    */
  def interval(period: Duration): Observable[Long] =
    fromJava(rx.Observable.interval(period.length, period.unit)).map(_.toLong)

  def timer(delay: Duration): Observable[Long] =
    fromJava(rx.Observable.timer(delay.length, delay.unit)).map(_.toLong)

  def pure[A](a: A): Observable[A] = rx.Observable.just(a)

  def apply[A](as: A*): Observable[A] =
    as match {
      case Nil => empty
      case x :: Nil => pure(x)
      case _ => rx.Observable.fromIterable(as.asJavaCollection)
    }

}


