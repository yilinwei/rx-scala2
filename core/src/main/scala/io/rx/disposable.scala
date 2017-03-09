package io.rx


import io.{reactivex => rx}

import implicits._

final class Disposable(val value: rx.disposables.Disposable) extends AnyVal { self =>
  def combine(that: Disposable): Disposable = {
    val c = new rx.disposables.CompositeDisposable
    c.addAll(self.value, that.value)
    c.asScala
  }

  def combineAll(that: Disposable*): Disposable = {
    val c = new rx.disposables.CompositeDisposable
    c.add(self.value)
    c.addAll(that.map(_.value): _ *)
    c.asScala
  }
}

object Disposable {

  val empty: Disposable = rx.disposables.Disposables.empty().asScala

  def combineAll(ds: Disposable*): Disposable =
    ds.headOption.map(_.combineAll(ds: _*)).getOrElse(empty)
}

