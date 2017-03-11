package io.rx

import java.util.concurrent.Callable

import scala.collection.JavaConverters._
import scala.collection.mutable
import io.{reactivex => rx}

import scala.util.{Failure, Success, Try}
import io.reactivex.functions._

import scala.collection.immutable.VectorBuilder
import io.rx.implicits._

import KConvert._

class Flowable[+A](val value: RxFlowable[Any]) { self =>

  @inline def asJava[AA >: A]: RxFlowable[AA] = value.asInstanceOf[RxFlowable[AA]]

  def filter(f: A => Boolean): Flowable[A] = asJava.filter(f.convertK1[Predicate]).asScala

  def withFilter(f: A => Boolean): Flowable.WithFilter[A] = new Flowable.WithFilter(f, self)

  def foreach(f: A => Unit): Disposable = asJava.forEach(f.convertK1[Consumer]).asScala

  def map[B](f: A => B): Flowable[B] =
  asJava[A].map[B](f.convertK[Function]).asScala

  def mapError(f: PartialFunction[Throwable, Throwable]): Flowable[A] =
    handle(f.andThen(t => Flowable.error[A](t)))

  def flatMap[B](f: A => Flowable[B]): Flowable[B] =
  asJava[A].flatMap(f.andThen(_.asJava[B]).convertK[Function]).asScala

  def flatten[B](implicit ev: A <:< Flowable[B]): Flowable[B] = {
    val o = map(_.asJava[B]).asJava[RxFlowable[B]]
    rx.Flowable.merge(o).asScala
  }

  def handle[AA >: A](f: PartialFunction[Throwable, Flowable[AA]]): Flowable[AA] = {
    val func = (t: Throwable) => if (f.isDefinedAt(t)) f(t).asJava[AA] else Flowable.error[A](t).asJava[AA]
    asJava[AA].onErrorResumeNext(func.convertK[Function]).asScala
  }

  def orElse[AA >: A](that: Flowable[AA]): Flowable[AA] =
  asJava[AA].onErrorResumeNext(that.asJava[AA]).asScala


  def scanLeft[B](b: B)(f: (B, A) => B): Flowable[B] =
  asJava[A].scan[B](b, f.convertK[BiFunction]).asScala

  def foldLeft[B](b: B)(f: (B, A) => B): Single[B] =
    asJava[A].reduce[B](b, f.convertK[BiFunction]).asScala

  def sample(duration: Duration)(implicit scheduler: Scheduler[Computation]): Flowable[A] =
    asJava.sample(duration.length, duration.unit, scheduler.asJava).asScala

  def debounce(duration: Duration)(implicit scheduler: Scheduler[Computation]): Flowable[A] =
    asJava.debounce(duration.length, duration.unit, scheduler.asJava).asScala

  def delay(duration: Duration)(implicit scheduler: Scheduler[Computation]): Flowable[A] =
    asJava.delay(duration.length, duration.unit, scheduler.asJava).asScala

  def uniqueWith[B](f: A => B): Flowable[A] =
    asJava.distinct(f.convertK[Function]).asScala

  def unique: Flowable[A] =
  asJava.distinct.asScala

  def distinct(f: (A, A) => Boolean): Flowable[A] =
    asJava[A].distinctUntilChanged(f.convertK1[BiPredicate]).asScala

  def distinct: Flowable[A] = distinct(_ == _)

  def drop(count: Long): Flowable[A] =
    asJava.skip(count).asScala

  def skip(duration: Duration)(implicit scheduler: Scheduler[Computation]): Flowable[A] =
    asJava.skip(duration.length, duration.unit, scheduler.asJava).asScala

  def dropWhile(f: A => Boolean): Flowable[A] =
    asJava.skipWhile(f.convertK1[Predicate]).asScala

  def tail: Flowable[A] = drop(1)

  def take(count: Long): Flowable[A] =
    asJava.take(count).asScala

  def take(duration: Duration): Flowable[A] =
    asJava.take(duration.length, duration.unit).asScala

  def takeWhile(f: A => Boolean): Flowable[A] =
    asJava.takeWhile(f.convertK1[Predicate]).asScala

  def takeUntil(f: A => Boolean): Flowable[A] =
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

  def zipWith[B, C](that: Flowable[B])(f: (A, B) => C): Flowable[C] =
    asJava[A].zipWith[B, C](that.asJava[B], f.convertK[BiFunction]).asScala

  def zipWith2[B, C, D](b: Flowable[B], c: Flowable[C])(f: (A, B, C) => D): Flowable[D] =
    rx.Flowable.zip[A, B, C, D](asJava, b.asJava, c.asJava, f.convertK[Function3]).asScala

  def zipWithLatest[B, C](that: Flowable[B])(f: (A, B) => C): Flowable[C] =
    rx.Flowable.combineLatest[A, B, C](self.asJava[A], that.asJava[B], f.convertK[BiFunction]).asScala

  def merge[AA >: A](that: Flowable[AA]): Flowable[AA] =
    rx.Flowable.merge(asJava[A], that.asJava[AA]).asScala

  def zip[B](that: Flowable[B]): Flowable[(A, B)] = zipWith(that)(_ -> _)

  def zip2[B, C](b: Flowable[B], c: Flowable[C]): Flowable[(A, B, C)] =
    zipWith2(b, c)((_, _, _))

  def subscribe(): Disposable = value.subscribe().asScala

  def groupBy[B, C](f: A => B, g: A => C): Flowable[(B, Flowable[C])] =
    groupBy(f).map { case (k, o) => k -> o.map(g) }

  def groupBy[B](f: A => B): Flowable[(B, Flowable[A])] =
    asJava[A].groupBy[B](f.convertK[Function]).asScala.map(o => o.getKey -> o.asScala)

  def toList: Single[List[A]] =
    foldLeft(List.empty[A])((b, a) => a :: b).map(_.reverse)

  def toVector: Single[Vector[A]] =
    foldLeft(new VectorBuilder[A])((b, a) => b += a).map(_.result())

  def toSet[AA >: A]: Single[Set[AA]] =
    foldLeft(new mutable.SetBuilder[AA, Set[AA]](Set()))((b, a) => b += a).map(_.result())

  def toSingle: Single[Option[A]] =
    map(Some(_)).asJava.single(None).asScala

  /** @see [[++]]*/
  def concat[AA >: A](that: Flowable[AA]): Flowable[AA] =
  asJava[AA].concatWith(that.asJava).asScala

  def ++[AA >: A](that: Flowable[AA]): Flowable[AA] =
  concat(that)

  def amb[AA >: A](that: Flowable[AA]): Flowable[AA] =
    asJava[AA].ambWith(that.asJava[AA]).asScala

  def onComplete(f: Try[Unit] => Unit): Disposable =
    asJava[A].subscribe(
      ((_: A) => ()).convertK1[Consumer],
      ((t: Throwable) => f(Failure(t))).convertK1[Consumer],
      (() => f(Success(()))).convert[Action]
    ).asScala

}

object Flowable {

  final class WithFilter[+A](p: A => Boolean, o: Flowable[A]) {
    def map[B](f: A => B): Flowable[B] = o.map(f)
    def flatMap[B, That](f: A => Flowable[B]): Flowable[B] = o.flatMap(f)
    def foreach(f: A => Unit): Unit = o.foreach(f)
    def withFilter(f: A => Boolean): WithFilter[A] = new WithFilter[A](a => p(a) && f(a), o)
  }

  def error[A](t: => Throwable): Flowable[A] = rx.Flowable.error((() => t).convertK[Callable]).asScala

  def empty[A]: Flowable[A] = rx.Flowable.empty[A].asScala

  def pure[A](a: A): Flowable[A] = rx.Flowable.just(a).asScala

  def fromSeq[A](seq: Seq[A]): Flowable[A] =
    rx.Flowable.fromIterable(seq.asJava).asScala

  def fromList[A](list: List[A]): Flowable[A] = list match {
    case Nil => empty
    case x :: Nil => pure(x)
    case xs => fromSeq(list)
  }
}