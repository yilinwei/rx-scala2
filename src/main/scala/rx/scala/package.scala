package rx

import io.{reactivex => jrx}

package object scala {
  implicit def convertRxObservableToObservable[A](o: jrx.Observable[A]): Observable[A] = Observable.fromJava(o)
  implicit def convertRxSingleToSingle[A](s: jrx.Single[A]): Single[A] = Single.fromJava(s)
  implicit def convertRxDisposableToDisposable(d: jrx.disposables.Disposable): Disposable = new Disposable(d)
}
