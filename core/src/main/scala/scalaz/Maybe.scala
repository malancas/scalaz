package scalaz

import Ordering._

/** An optional value
 *
 * A `Maybe[A]` will either be a wrapped `A` instance (`Just`), or a lack of underlying
 * `A` instance (`Empty`).
 *
 * `Maybe[A]` is isomorphic to `Option[A]`, however there are some differences between
 * the two. `Maybe` is invariant in `A` while `Option` is covariant. `Maybe[A]` does not expose
 * an unsafe `get` operation to access the underlying `A` value (that may not exist) like
 * `Option[A]` does. `Maybe[A]` does not come with an implicit conversion to `Iterable[A]` (a
 * trait with over a dozen super types).
 *
 * Unfortunately, representing `Empty` as a singleton object while avoiding covariance on
 * `Maybe` requires jumping through some Scala hoops. To avoid exposing said hoops,
 * case matching on `Maybe` is not supported and [[cata]] (or other methods) should be
 * used instead.
 */
sealed abstract class Maybe[A] {
  import Maybe._

  final def cata[B](f: A => B, b: => B): B =
    this match {
      case Just(a) => f(a)
      case Empty() => b
    }

  final def toFailure[B](b: => B): Validation[A, B] =
    cata(Failure(_), Success(b))

  final def toSuccess[B](b: => B): Validation[B, A] =
    cata(Success(_), Failure(b))

  final def toLeft[B](b: => B): A \/ B =
    cata(\/.left(_), \/.right(b))

  final def toRight[B](b: => B): B \/ A =
    cata(\/.right(_), \/.left(b))

  final def isJust: Boolean =
    cata(_ => true, false)

  final def isEmpty: Boolean =
    cata(_ => false, true)

  final def map[B](f: A => B): Maybe[B] =
    cata(f andThen just[B], empty[B])

  final def flatMap[B](f: A => Maybe[B]) =
    cata(f, empty[B])

  final def toOption: Option[A] =
    cata(Some(_), None)

  final def orElse(oa: => Maybe[A]): Maybe[A] =
    cata(_ => this, oa)

  final def first: FirstMaybe[A] = Tag(this)

  final def last: LastMaybe[A] = Tag(this)

  final def min: MinMaybe[A] = Tag(this)

  final def max: MaxMaybe[A] = Tag(this)
}

final case class Empty[A]() extends Maybe[A]

final case class Just[A](a: A) extends Maybe[A]

object Maybe extends MaybeFunctions with MaybeInstances

sealed trait MaybeFunctions {
  import Maybe._

  final def empty[A]: Maybe[A] = Empty()

  final def just[A](a: A): Maybe[A] = Just(a)

  final def fromOption[A](oa: Option[A]): Maybe[A] =
    std.option.cata(oa)(just, empty)
}

sealed trait MaybeInstances {
  import Maybe._

  implicit def maybeEqual[A : Equal]: Equal[Maybe[A]] = new MaybeEqual[A] {
    def A = implicitly
  }

  implicit def maybeOrder[A : Order]: Order[Maybe[A]] = new Order[Maybe[A]] with MaybeEqual[A] {
    def A = implicitly

    def order(fa1: Maybe[A], fa2: Maybe[A]) =
      fa1.cata(
        a1 => fa2.cata(
          a2 => Order[A].order(a1, a2),
          GT),
        fa2.cata(_ => LT, EQ))
  }

  implicit def maybeShow[A](implicit A: Show[A]): Show[Maybe[A]] =
    Show.show(_.cata(
      a => Cord("Just(", A.show(a), ")"),
      "Empty"))

  implicit def maybeMonoid[A](implicit A: Semigroup[A]): Monoid[Maybe[A]] = new Monoid[Maybe[A]] {
    def append(fa1: Maybe[A], fa2: => Maybe[A]) =
      fa1.cata(
        a1 => fa2.cata(a2 => just(A.append(a1, a2)), fa1),
        fa2.cata(_ => fa2, empty))

    def zero = empty
  }

  implicit def maybeFirstMonoid[A]: Monoid[FirstMaybe[A]] = new Monoid[FirstMaybe[A]] {
    val zero: FirstMaybe[A] = Tag(empty)

    def append(fa1: FirstMaybe[A], fa2: => FirstMaybe[A]): FirstMaybe[A] = Tag(fa1.orElse(fa2))
  }

  implicit def maybeFirstShow[A](implicit A: Show[Maybe[A]]): Show[FirstMaybe[A]] = Tag.subst(A)

  implicit def maybeFirstOrder[A](implicit A: Order[Maybe[A]]): Order[FirstMaybe[A]] = Tag.subst(A)

  implicit val maybeFirstMonad: Monad[FirstMaybe] = new Monad[FirstMaybe] {
    def point[A](a: => A): FirstMaybe[A] = Tag(just(a))
    override def map[A, B](fa: FirstMaybe[A])(f: A => B) = Tag(fa map f)
    def bind[A, B](fa: FirstMaybe[A])(f: A => FirstMaybe[B]): FirstMaybe[B] = Tag(fa flatMap f)
  }

  implicit def maybeLastMonoid[A]: Monoid[LastMaybe[A]] = new Monoid[LastMaybe[A]] {
    val zero: LastMaybe[A] = Tag(empty)

    def append(fa1: LastMaybe[A], fa2: => LastMaybe[A]): LastMaybe[A] = Tag(fa2.orElse(fa1))
  }

  implicit def maybeLastShow[A](implicit A: Show[Maybe[A]]): Show[LastMaybe[A]] = Tag.subst(A)

  implicit def maybeLastOrder[A](implicit A: Order[Maybe[A]]): Order[LastMaybe[A]] = Tag.subst(A)

  implicit val maybeLastMonad: Monad[LastMaybe] = new Monad[LastMaybe] {
    def point[A](a: => A): LastMaybe[A] = Tag(just(a))
    override def map[A, B](fa: LastMaybe[A])(f: A => B) = Tag(fa map f)
    def bind[A, B](fa: LastMaybe[A])(f: A => LastMaybe[B]): LastMaybe[B] = Tag(fa flatMap f)
  }

  implicit def maybeMin[A](implicit o: Order[A]) = new Monoid[MinMaybe[A]] {
    def zero: MinMaybe[A] = Tag(empty)

    def append(f1: MinMaybe[A], f2: => MinMaybe[A]) = Tag(Order[Maybe[A]].min(f1, f2))
  }

  implicit def maybeMinShow[A: Show]: Show[MinMaybe[A]] = Tag.subst(Show[Maybe[A]])

  implicit def maybeMinOrder[A: Order]: Order[MinMaybe[A]] = Tag.subst(Order[Maybe[A]])

  implicit def maybeMinMonad: Monad[MinMaybe] = new Monad[MinMaybe] {
    def point[A](a: => A): MinMaybe[A] = Tag(just(a))
    override def map[A, B](fa: MinMaybe[A])(f: A => B) = Tag(fa map f)
    def bind[A, B](fa: MinMaybe[A])(f: A => MinMaybe[B]): MinMaybe[B] = Tag(fa flatMap f)
  }

  implicit def maybeMax[A](implicit o: Order[A]) = new Monoid[MaxMaybe[A]] {
    def zero: MaxMaybe[A] = Tag(empty)

    def append(f1: MaxMaybe[A], f2: => MaxMaybe[A]) = Tag(Order[Maybe[A]].max(f1, f2))
  }

  implicit def maybeMaxShow[A: Show]: Show[MaxMaybe[A]] = Tag.subst(Show[Maybe[A]])

  implicit def maybeMaxOrder[A: Order]: Order[MaxMaybe[A]] = Tag.subst(Order[Maybe[A]])

  implicit def maybeMaxMonad: Monad[MaxMaybe] = new Monad[MaxMaybe] {
    def point[A](a: => A): MaxMaybe[A] = Tag(just(a))
    override def map[A, B](fa: MaxMaybe[A])(f: A => B) = Tag(fa map f)
    def bind[A, B](fa: MaxMaybe[A])(f: A => MaxMaybe[B]): MaxMaybe[B] = Tag(fa flatMap f)
  }

  implicit val maybeInstance = new Traverse[Maybe] with MonadPlus[Maybe] with Cozip[Maybe] with Zip[Maybe] with Unzip[Maybe] with Align[Maybe] with IsEmpty[Maybe] with Cobind[Maybe] with Optional[Maybe] {

    def point[A](a: => A) = just(a)

    override def ap[A, B](fa: => Maybe[A])(mf: => Maybe[A => B]) =
      mf.cata(f => fa.cata(f andThen just, empty), empty)

    def bind[A, B](fa: Maybe[A])(f: A => Maybe[B]) = fa flatMap f

    override def map[A, B](fa: Maybe[A])(f: A => B) = fa map f

    def traverseImpl[F[_], A, B](fa: Maybe[A])(f: A => F[B])(implicit F: Applicative[F]) =
      fa.cata(a => F.map(f(a))(just), F.point(empty))

    def empty[A]: Maybe[A] = Maybe.empty

    def plus[A](a: Maybe[A], b: => Maybe[A]) = a orElse b

    override def foldRight[A, B](fa: Maybe[A], z: => B)(f: (A, => B) => B) =
      fa.cata(f(_, z), z)

    def cozip[A, B](fa: Maybe[A \/ B]) =
      fa.cata(_.leftMap(just).map(just), \/.left(empty))

    def zip[A, B](a: => Maybe[A], b: => Maybe[B]) =
      for {
        x <- a
        y <- b
      } yield (x, y)

    def unzip[A, B](a: Maybe[(A, B)]) =
      a.cata(ab => (just(ab._1), just(ab._2)), (empty, empty))

    def alignWith[A, B, C](f: A \&/ B => C) = (fa, fb) =>
      fa.cata(
        a => fb.cata(
          b => just(f(\&/.Both(a, b))),
          just(f(\&/.This(a)))),
        fb.cata(
          b => just(f(\&/.That(b))),
          empty))

    def cobind[A, B](fa: Maybe[A])(f: Maybe[A] => B) =
      fa.map(a => f(just(a)))

    override def cojoin[A](a: Maybe[A]) =
      a.map(just)

    def pextract[B, A](fa: Maybe[A]): Maybe[B] \/ A =
      fa.cata(\/.right, \/.left(empty))
    override def isDefined[A](fa: Maybe[A]): Boolean = fa.isJust
    override def toOption[A](fa: Maybe[A]): Option[A] = fa.toOption
  }
}

private sealed trait MaybeEqual[A] extends Equal[Maybe[A]] {
  implicit def A: Equal[A]

  override final def equal(fa1: Maybe[A], fa2: Maybe[A]) =
    fa1.cata(
      a1 => fa2.cata(a2 => A.equal(a1, a2), false),
      fa2.cata(_ => false, true))
}
