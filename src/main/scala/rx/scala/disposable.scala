package rx.scala


import io.{reactivex => rx}

final class Disposable(val value: rx.disposables.Disposable) extends AnyVal { self =>
  def combine(that: Disposable): Disposable = {
    val c = new rx.disposables.CompositeDisposable
    c.addAll(self.value, that.value)
    c
  }

  def combineAll(that: Disposable*): Disposable = {
    val c = new rx.disposables.CompositeDisposable
    c.add(self.value)
    c.addAll(that.map(_.value): _ *)
    c
  }
}

object Disposable {

  val empty: Disposable = rx.disposables.Disposables.empty()

  def combineAll(ds: Disposable*): Disposable =
    ds.headOption.map(_.combineAll(ds: _*)).getOrElse(empty)
}

