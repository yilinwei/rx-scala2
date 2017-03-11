package io.rx

import java.util.concurrent.Callable

import scala.collection.JavaConverters._
import io.{reactivex => rx}

import scala.util.{Failure, Success, Try}
import io.reactivex.functions._

import scala.collection.immutable.VectorBuilder
import io.rx.implicits._

import scala.collection.mutable


import KConvert._

class Observable[+A](val value: RxObservable[Any]) { self =>

  @inline def asJava[AA >: A]: RxObservable[AA] = value.asInstanceOf[RxObservable[AA]]

  /**
    * Example:
    * {{{
    *   scala> import io.rx._
    *   scala> import io.rx.implicits._
    *   scala> val o = Observable(1, 2, 3)
    *   scala> val res = o.filter(_ > 2)
    *   scala> Await.result(res.toList, 1 second)
    *   res0: List[Int] = List(3)
    * }}}
    */
  def filter(f: A => Boolean): Observable[A] = asJava.filter(f.convertK1[Predicate]).asScala

  def withFilter(f: A => Boolean): Observable.WithFilter[A] = new Observable.WithFilter(f, self)

  def foreach(f: A => Unit): Disposable = asJava.forEach(f.convertK1[Consumer]).asScala

  /**
    * Example:
    * {{{
    *   scala> import io.rx._
    *   scala> import io.rx.implicits._
    *   scala> val o = Observable("foo", "bar")
    *   scala> val res = o.map(_.length)
    *   scala> Await.result(res.toList, 1 second)
    *   res0: List[Int] = List(3, 3)
    * }}}
    */
  def map[B](f: A => B): Observable[B] =
  asJava[A].map[B](f.convertK[Function]).asScala

  def mapError(f: PartialFunction[Throwable, Throwable]): Observable[A] =
    handle(f.andThen(t => Observable.error[A](t)))

  /**
    * Example:
    * {{{
    *   scala> import io.rx._
    *   scala> import io.rx.implicits._
    *   scala> val o = Observable(1, 2)
    *   scala> val res = o.flatMap(a => Observable(a + 1, a + 2))
    *   scala> Await.result(res.toList, 1 second)
    *   res0: List[Int] = List(2, 3, 3, 4)
    * }}}
    */
  def flatMap[B](f: A => Observable[B]): Observable[B] =
  asJava[A].flatMap(f.andThen(_.asJava[B]).convertK[Function]).asScala

  /**
    * Example:
    * {{{
    *   scala> import io.rx._
    *   scala> import io.rx.implicits._
    *   scala> val o = Observable(Observable(1, 2), Observable(3, 4))
    *   scala> val res = o.flatten
    *   scala> Await.result(res.toList, 1 second)
    *   res0: List[Int] = List(1, 2, 3, 4)
    * }}}
    */
  def flatten[B](implicit ev: A <:< Observable[B]): Observable[B] = {
    val o = map(_.asJava[B]).asJava[rx.Observable[B]]
    rx.Observable.merge(o).asScala
  }

  /**
    * Example:
    * {{{
    *   scala> import io.rx._
    *   scala> import io.rx.implicits._
    *   scala> val o = Observable.error[Int](new IllegalArgumentException("foo"))
    *   scala> val res = o.handle { case t: IllegalArgumentException => Observable(1) }
    *   scala> Await.result(res.toList, 1 second)
    *   res0: List[Int] = List(1)
    * }}}
    */
  def handle[AA >: A](f: PartialFunction[Throwable, Observable[AA]]): Observable[AA] = {
    val func = (t: Throwable) => if (f.isDefinedAt(t)) f(t).asJava[AA] else Observable.error[A](t).asJava[AA]
    asJava[AA].onErrorResumeNext(func.convertK[Function]).asScala
  }

  /**
    * Example:
    * {{{
    *   scala> import io.rx._
    *   scala> import io.rx.implicits._
    *   scala> val o1 = Observable.error[Int](new IllegalArgumentException("foo"))
    *   scala> val o2 = Observable(1)
    *   scala> val res = o1.orElse(o2)
    *   scala> Await.result(res.toList, 1 second)
    *   res0: List[Int] = List(1)
    * }}}
    */
  def orElse[AA >: A](that: Observable[AA]): Observable[AA] =
  asJava[AA].onErrorResumeNext(that.asJava[AA]).asScala


  /**
    * Example:
    * {{{
    *   scala> import io.rx._
    *   scala> import io.rx.implicits._
    *   scala> val o = Observable(1, 2, 3, 4, 5)
    *   scala> val res = o.scanLeft(0)(_ + _)
    *   scala> Await.result(res.toList, 1 second)
    *   res0: List[Int] = List(0, 1, 3, 6, 10, 15)
    * }}}
    */
  def scanLeft[B](b: B)(f: (B, A) => B): Observable[B] =
  asJava[A].scan[B](b, f.convertK[BiFunction]).asScala

  /**
    * Example:
    * {{{
    *   scala> import io.rx._
    *   scala> import io.rx.implicits._
    *   scala> val o = Observable(1, 2, 3)
    *   scala> val res = o.foldLeft(10)(_ - _)
    *   scala> Await.result(res, 1 second)
    *   res0: Int = 4
    * }}}
    */
  def foldLeft[B](b: B)(f: (B, A) => B): Single[B] =
    asJava[A].reduce[B](b, f.convertK[BiFunction]).asScala

  def sample(duration: Duration)(implicit scheduler: Scheduler[Computation]): Observable[A] =
    asJava.sample(duration.length, duration.unit, scheduler.asJava).asScala

  def debounce(duration: Duration)(implicit scheduler: Scheduler[Computation]): Observable[A] =
    asJava.debounce(duration.length, duration.unit, scheduler.asJava).asScala

  def delay(duration: Duration)(implicit scheduler: Scheduler[Computation]): Observable[A] =
    asJava.delay(duration.length, duration.unit, scheduler.asJava).asScala

  def uniqueWith[B](f: A => B): Observable[A] =
    asJava.distinct(f.convertK[Function]).asScala

  /**
    * Example:
    * {{{
    *   scala> import io.rx._
    *   scala> import io.rx.implicits._
    *   scala> val o = Observable(1, 1, 3, 4)
    *   scala> val res = o.distinct
    *   scala> Await.result(res.toList, 1 second)
    *   res0: List[Int] = List(1, 3, 4)
    * }}}
    */
  def unique: Observable[A] =
  asJava.distinct.asScala

  def distinct(f: (A, A) => Boolean): Observable[A] =
    asJava[A].distinctUntilChanged(f.convertK1[BiPredicate]).asScala

  def distinct: Observable[A] = distinct(_ == _)

  def drop(count: Long): Observable[A] =
    asJava.skip(count).asScala

  def skip(duration: Duration)(implicit scheduler: Scheduler[Computation]): Observable[A] =
    asJava.skip(duration.length, duration.unit, scheduler.asJava).asScala

  def dropWhile(f: A => Boolean): Observable[A] =
    asJava.skipWhile(f.convertK1[Predicate]).asScala

  def tail: Observable[A] = drop(1)

  def take(count: Long): Observable[A] =
    asJava.take(count).asScala

  def take(duration: Duration): Observable[A] =
    asJava.take(duration.length, duration.unit).asScala

  def takeWhile(f: A => Boolean): Observable[A] =
    asJava.takeWhile(f.convertK1[Predicate]).asScala

  def takeUntil(f: A => Boolean): Observable[A] =
    asJava.takeUntil(f.convertK1[Predicate]).asScala

  def get(index: Long): Single[Option[A]] =
    map(Option(_)).asJava.elementAt(index).toSingle(None).asScala

  private def elseDefault[AA >: A](s: Single[Option[AA]], a: => AA): Single[AA] =
    s.map((o: Option[AA]) => o.getOrElse(a))

  def getOrElse[AA >: A](index: Long, a: => AA): Single[AA] =
    elseDefault(get(index), a)

  def head: Single[Option[A]] =
    map(Option(_)).asJava.firstElement().toSingle(None).asScala

  def headOrElse[AA >: A](a: => AA): Single[AA] =
    elseDefault(head, a)

  def last: Single[Option[A]] = map(Option(_)).asJava.last(None).asScala

  def lastOrElse[AA >: A](a: => AA): Single[AA] =
    elseDefault(last, a)

  def zipWith[B, C](that: Observable[B])(f: (A, B) => C): Observable[C] =
    asJava[A].zipWith[B, C](that.asJava[B], f.convertK[BiFunction]).asScala

  def zipWith2[B, C, D](b: Observable[B], c: Observable[C])(f: (A, B, C) => D): Observable[D] =
    rx.Observable.zip[A, B, C, D](asJava, b.asJava, c.asJava, f.convertK[Function3]).asScala

  def zipWithLatest[B, C](that: Observable[B])(f: (A, B) => C): Observable[C] =
    rx.Observable.combineLatest[A, B, C](self.asJava[A], that.asJava[B], f.convertK[BiFunction]).asScala

  def merge[AA >: A](that: Observable[AA]): Observable[AA] =
    rx.Observable.merge(asJava[A], that.asJava[AA]).asScala

  def zip[B](that: Observable[B]): Observable[(A, B)] = zipWith(that)(_ -> _)

  def zip2[B, C](b: Observable[B], c: Observable[C]): Observable[(A, B, C)] =
    zipWith2(b, c)((_, _, _))

  def subscribe(): Disposable = value.subscribe().asScala

  def groupBy[B, C](f: A => B, g: A => C): Observable[(B, Observable[C])] =
    groupBy(f).map { case (k, o) => k -> o.map(g) }

  def groupBy[B](f: A => B): Observable[(B, Observable[A])] =
    asJava[A].groupBy[B](f.convertK[Function]).asScala.map(o => o.getKey -> o.asScala)

  def drain[B](subject: Subject[A, B]): Observable[B] = subject(self)

  def drain2[AA >: A, B](that: Observable[AA])(subject: Subject[AA, B]): Observable[B] = merge(that).drain(subject)

  def toList: Single[List[A]] =
    foldLeft(List.empty[A])((b, a) => a :: b).map(_.reverse)

  def toVector: Single[Vector[A]] =
    foldLeft(new VectorBuilder[A])((b, a) => b += a).map(_.result())

  def toSet[AA >: A]: Single[Set[AA]] =
    foldLeft(new mutable.SetBuilder[AA, Set[AA]](Set()))((b, a) => b += a).map(_.result())

  def toSingle: Single[Option[A]] =
    map(Some(_)).asJava.single(None).asScala

  /** @see [[++]]*/
  def concat[AA >: A](that: Observable[AA]): Observable[AA] =
  asJava[AA].concatWith(that.asJava).asScala

  /**
    * Example:
    * {{{
    *   scala> import io.rx._
    *   scala> import io.rx.implicits._
    *   scala> val o1 = Observable(1, 2)
    *   scala> val o2 = Observable(3, 4)
    *   scala> val res = o1 ++ o2
    *   scala> Await.result(res.toList, 1 second)
    *   res0 = List(1, 2, 3, 4)
    * }}}
    *
    */
  def ++[AA >: A](that: Observable[AA]): Observable[AA] =
  concat(that)

  def amb[AA >: A](that: Observable[AA]): Observable[AA] =
    asJava[AA].ambWith(that.asJava[AA]).asScala

  def onComplete(f: Try[Unit] => Unit): Disposable =
    asJava[A].subscribe(
      ((_: A) => ()).convertK1[Consumer],
      ((t: Throwable) => f(Failure(t))).convertK1[Consumer],
      (() => f(Success(()))).convert[Action]
    ).asScala

}


object Observable {

  final class WithFilter[+A](p: A => Boolean, o: Observable[A]) {
    def map[B](f: A => B): Observable[B] = o.map(f)
    def flatMap[B, That](f: A => Observable[B]): Observable[B] = o.flatMap(f)
    def foreach(f: A => Unit): Unit = o.foreach(f)
    def withFilter(f: A => Boolean): WithFilter[A] = new WithFilter[A](a => p(a) && f(a), o)
  }

  def never[A]: Observable[A] = rx.Observable.never[A].asScala

  def empty[A]: Observable[A] = rx.Observable.empty[A]().asScala

  def error[A](t: => Throwable): Observable[A] = rx.Observable.error((() => t).convertK[Callable]).asScala

  def suspend[A](a: Observable[A]): Observable[A] = rx.Observable.defer((() => a.asJava[A]).convertK[Callable]).asScala

  /**
    * Example:
    * {{{
    *   scala> import io.rx._
    *   scala> import io.rx.implicits._
    *   scala> val o = Observable.iterate(0)(_ + 1)
    *   scala> val res = o.take(4)
    *   scala> Await.result(res.toList, 1 second)
    *   res0: List[Int] = List(0, 1, 2, 3)
    * }}}
    */
  def iterate[A](a: A)(f: A => A): Observable[A] =
  pure(a) ++ rx.Observable
    .generate[A, A](
    (() => a).convertK[Callable],
    ((a: A, emitter: rx.Emitter[A]) => {
      val next = f(a)
      emitter.onNext(next)
      next
    }).convertK[BiFunction]).asScala

  /**
    * Example:
    * {{{
    *   scala> import io.rx._
    *   scala> import io.rx.implicits._
    *   scala> val res = Observable.iterateWhile(0)(a => if(a < 3) Some(a + 1) else None)
    *   scala> Await.result(res.toList, 1 second)
    *   res0: List[Int] = List(0, 1, 2, 3)
    * }}}
    */
  def iterateWhile[A](a: A)(f: A => Option[A]): Observable[A] = {
    pure(a) ++ rx.Observable.generate(
      (() => Some(a): Option[A]).convertK[Callable],
      ((a: Option[A], emitter: rx.Emitter[A]) => {
        a.map(f).flatMap {
          case None =>
            emitter.onComplete()
            None
          case next@Some(value) =>
            emitter.onNext(value)
            next
        }
      }).convertK[BiFunction]).asScala
  }



  /**
    * Example:
    * {{{
    *   scala> import io.rx._
    *   scala> import io.rx.implicits._
    *   scala> val res = Observable.range(0, 3)
    *   res0: List[Int] = List(0, 1, 2, 3)
    * }}}
    */
  def range(start: Int, end: Int): Observable[Int] = {
    require(end >= start)
    if (start == end) empty else rx.Observable.range(start, end - start).asScala.map(_.toInt)
  }

  def range(start: Long, end: Long): Observable[Long] = {
    require(end >= start)
    if(start == end) empty else rx.Observable.rangeLong(start, end - start).asScala.map(_.toLong)
  }

  /**
    * Example:
    * {{{
    *   scala> import io.rx._
    *   scala> import io.rx.implicits._
    *   scala> val interval = Observable.interval(100 milliseconds)
    *   scala> val res = interval.take(150 milliseconds)
    *   scala> Await.result(res.toList, 1 second).size
    *   res0: Int = 1
    * }}}
    */
  def interval(period: Duration)(implicit scheduler: Scheduler[Computation]): Observable[Long] =
  rx.Observable.interval(period.length, period.unit, scheduler.asJava).asScala.map(_.toLong)

  def pure[A](a: A): Observable[A] = rx.Observable.just(a).asScala

  def apply[A](as: A*): Observable[A] = fromSeq(as)

  def fromSeq[A](seq: Seq[A]): Observable[A] =
    rx.Observable.fromIterable(seq.asJavaCollection).asScala

  def fromList[A](list: List[A]): Observable[A] = list match {
    case Nil => empty
    case x :: Nil => pure(x)
    case xs => fromSeq(xs)
  }

  def fromTry[A](_try: Try[A]): Observable[A] = _try match {
    case Success(a) => pure(a)
    case Failure(e) => error(e)
  }


}

