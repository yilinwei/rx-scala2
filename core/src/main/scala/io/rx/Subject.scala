package io.rx

import io.reactivex.functions._
import io.reactivex.{subjects => rx}

import implicits._

import KConvert._

final class Subject[-A, +B](val repr: RxSubject[Any], f: A => B) { self =>
  def asJava[AA <: A]: rx.Subject[AA] = repr.asInstanceOf[rx.Subject[AA]]
  def apply(o: Observable[A]): Observable[B] = o.asJava.subscribeWith(asJava[A]).map[B](f.convertK[Function]).asScala
  def contramap[AA](f: AA => A): Subject[AA, B] = new Subject[AA, B](repr, f.andThen(self.f))
  def map[BB](f: B => BB): Subject[A, BB] = new Subject[A, BB](repr, self.f.andThen(f))
}

object Subject {
  def replay[A](max: Int): Subject[A, A] = rx.ReplaySubject.createWithSize[A](max).asScala
  def replay[A](expire: Duration)(implicit scheduler: Scheduler[Computation]): Subject[A, A] =
    rx.ReplaySubject.createWithTime[A](expire.length, expire.unit, scheduler.asJava).asScala
  def publish[A]: Subject[A, A] = rx.PublishSubject.create[A]().asScala
  def behaviour[A]: Subject[A, A] = rx.BehaviorSubject.create[A]().asScala
}
