package rx

import io.{reactivex => jrx}

/**
  * Created by yilin on 04/03/17.
  */
package object scala {
  implicit def convertFunctionToRxFunction[A, B](f: A => B): jrx.functions.Function[_ >: A, _ <: B] = a => f(a)
  implicit def convertRxObservableToObservable[A](o: jrx.Observable[A]): Observable[A] =
    Observable.fromJava(o)
  implicit def convertRxSingleToSingle[A](s: jrx.Single[A]): Single[A] =
    Single.fromJava(s)
  implicit def convertRxDisposableToDisposable(d: jrx.disposables.Disposable): Disposable =
    new Disposable(d)
}
