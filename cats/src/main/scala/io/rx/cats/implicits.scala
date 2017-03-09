package io.rx.cats

import cats._

import io.rx.{Observable, Single}

final class ObservableOps[A](o: Observable[A]) {

  def scan[AA >: A, M](implicit M: Monoid[AA]): Observable[AA] =
    o.scanLeft(M.empty)(M.combine)

  def scanK[F[_], AA](implicit M: MonoidK[F], ev: A <:< F[AA]): Observable[F[AA]] =
    o.scanLeft(M.empty[AA])((b, a) => M.combineK(b, a))

  def fold[AA >: A, M](implicit M: Monoid[AA]): Single[AA] =
    o.foldLeft(M.empty)(M.combine)

  def foldK[F[_], AA](implicit M: MonoidK[F], ev: A <:< F[AA]): Single[F[AA]] =
    o.foldLeft(M.empty[AA])((b, a) => M.combineK(b, a))
}

final class ObservableAlgebra extends MonadError[Observable, Throwable] {

  def flatMap[A, B](fa: Observable[A])(f: A => Observable[B]): Observable[B] =
    fa.flatMap(f)

  override def map[A, B](fa: Observable[A])(f: A => B): Observable[B] =
    fa.map(f)

  def tailRecM[A, B](a: A)(f: A => Observable[Either[A, B]]): Observable[B] =
    f(a).flatMap {
      case Left(aa) => tailRecM(aa)(f)
      case Right(b) => Observable.pure(b)
    }

  def raiseError[A](e: Throwable): Observable[A] =
    Observable.error(e)

  def handleErrorWith[A](fa: Observable[A])(f: Throwable => Observable[A]): Observable[A] = fa.handle { case t => f(t) }

  def pure[A](x: A): Observable[A] = Observable.pure(x)
}

trait CatsSyntax {
  implicit def observableToCatsObservableOps[A](o: Observable[A]): ObservableOps[A] = new ObservableOps(o)
}

object implicits extends CatsSyntax


