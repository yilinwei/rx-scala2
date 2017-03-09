package io.rx

object implicits extends AllSyntax

import scala.concurrent.duration._

trait DurationSyntax {
  implicit def intDurationOps(i: Int): DurationInt = new DurationInt(i)
  implicit def longDurationOps(l: Long): DurationLong = new DurationLong(l)
  implicit def doubleDurationOps(d: Double): DurationDouble = new DurationDouble(d)
}

trait AllSyntax extends DurationSyntax with JavaConversionSyntax

final class RxObservableOps[A](val value: RxObservable[A]) extends AnyVal {
  def asScala: Observable[A] = new Observable(value.asInstanceOf[RxObservable[Any]])
}

final class RxSingleOps[A](val value: RxSingle[A]) extends AnyVal {
  def asScala: Single[A] = new Single[A](value.asInstanceOf[RxSingle[Any]])
}

final class RxDisposableOps(val value: RxDisposable) extends AnyVal {
  def asScala: Disposable = new Disposable(value)
}

final class RxSubjectOps[A](val value: RxSubject[A]) extends AnyVal {
  def asScala: Subject[A, A] = new Subject[A, A](value.asInstanceOf[RxSubject[Any]], identity)
}

trait JavaConversionSyntax {
  implicit def rxObservableToRxObservableOps[A](o: RxObservable[A]): RxObservableOps[A] = new RxObservableOps(o)
  implicit def rxSingleToRxSingleOps[A](s: RxSingle[A]): RxSingleOps[A] = new RxSingleOps(s)
  implicit def rxDisposableToRxDisposableOps(d: RxDisposable): RxDisposableOps = new RxDisposableOps(d)
  implicit def rxSubjectToRxSubjectOps[A](s: RxSubject[A]): RxSubjectOps[A] = new RxSubjectOps(s)
}
