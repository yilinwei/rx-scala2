package io.rx

import io.{reactivex => rx}
import io.reactivex.schedulers.{Schedulers => RxSchedulers}

final class Scheduler[A](val value: rx.Scheduler) extends AnyVal {
  def asJava: rx.Scheduler = value
}

trait IO
trait NewThread
trait Computation

object Scheduler {
  implicit lazy val ioScheduler: Scheduler[IO] = new Scheduler[IO](RxSchedulers.io())
  implicit lazy val computationScheduler: Scheduler[Computation] = new Scheduler[Computation](RxSchedulers.computation())
  implicit lazy val newThreadScheduler: Scheduler[NewThread] = new Scheduler[NewThread](RxSchedulers.newThread())
}



