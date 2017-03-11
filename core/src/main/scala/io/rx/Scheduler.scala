package io.rx

import io.{reactivex => rx}
import io.reactivex.schedulers.{Schedulers => RxSchedulers}

final class Scheduler[A](val value: rx.Scheduler) extends AnyVal {
  def asJava: rx.Scheduler = value
}

trait IO
trait NewThread
trait Computation
trait Trampoline

object Scheduler {
  implicit val ioScheduler: Scheduler[IO] = new Scheduler[IO](RxSchedulers.io())
  implicit val computationScheduler: Scheduler[Computation] = new Scheduler[Computation](RxSchedulers.computation())
  implicit val newThreadScheduler: Scheduler[NewThread] = new Scheduler[NewThread](RxSchedulers.newThread())
  implicit val trampolineScheduler: Scheduler[Trampoline] = new Scheduler[Trampoline](RxSchedulers.trampoline())
}



