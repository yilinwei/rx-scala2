package rx

package object scala {

  implicit final class RxObservableOps[A](val value: io.reactivex.Observable[A]) extends AnyVal {
    def asScala: rx.scala.Observable[A] = rx.scala.Observable.fromJava(value)
  }

  implicit final class RxSingleOps[A](val value: io.reactivex.Single[A]) extends AnyVal{
    def asScala: rx.scala.Single[A] = rx.scala.Single.fromJava(value)
  }

  implicit final class RxDisposableOps[A](val value: io.reactivex.disposables.Disposable) extends AnyVal{
    def asScala: rx.scala.Disposable = new rx.scala.Disposable(value)
  }
}

