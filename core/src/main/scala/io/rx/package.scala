package io

package object rx {

  val Await = scala.concurrent.Await

  type Duration = scala.concurrent.duration.Duration

  type RxObservable[A] = io.reactivex.Observable[A]
  type RxSingle[A] = io.reactivex.Single[A]
  type RxSubject[A] = io.reactivex.subjects.Subject[A]
  type RxDisposable = io.reactivex.disposables.Disposable
  type RxFlowable[A] = io.reactivex.Flowable[A]

}

