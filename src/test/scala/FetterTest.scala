import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.language.postfixOps
import scala.reflect.ClassTag

class FetterTest extends AnyFlatSpec with should.Matchers {
  sealed trait Expression {
    def x: Expression = Select(this, "x")
    def y: Expression = Select(this, "y")
    def func(arg: Any) = Select(this, s"func($arg)")
    def funcGeneric[T](arg: Any)(implicit t: ClassTag[T]) =
      Select(this, s"funcGeneric[${t.runtimeClass.getName}]($arg)")
    def varGeneric[T](implicit t: ClassTag[T]) =
      Select(this, s"varGeneric[${t.runtimeClass.getSimpleName}]")
    def map(f: Expression => Expression): Expression = Map(this, f(Value("x$1")))
    def flatMap(f: Expression => Expression): Expression = FlatMap(this, f(Value("x$1")))
  }
  case class Value(name: String) extends Expression
  case class Select(base: Expression, selection: String) extends Expression
  case class FlatMap(original: Expression, lambdaResult: Expression) extends Expression
  case class Map(original: Expression, lambdaResult: Expression) extends Expression

  case class Foo(bar: Int) {
    def opt: Option[Foo] = Some(this)
    def ident: Foo = this
    def generic[T]: Foo = this
    def func(x: Int): Foo = Foo(x)
    def genericFunc[T](x: Int): Foo = Foo(x)
  }

  "Expansion" should "be correct" in {
    val base = Value("base")

    base?>x.y should be(base.map(_.x.y))
    base.x.y??>x.y??>y.x?>x.y should be(base.x.y.flatMap(_.x.y.flatMap(_.y.x.map(_.x.y))))
    base.func("hi")?>func("hello").y??>x should be(base.func("hi").map(_.func("hello").y.flatMap(_.x)))
    base.funcGeneric[String]("hi")?>funcGeneric[Int]("hello").y??>x should be(
      base.funcGeneric[String]("hi").map(_.funcGeneric[Int]("hello").y.flatMap(_.x))
    )
    base.varGeneric[String]?>varGeneric[Int].y??>x should be(
      base.varGeneric[String].map(_.varGeneric[Int].y.flatMap(_.x))
    )
    ({ var v = 0; v += 1; base })?>x should be({ var v = 0; v += 1; base }.map(_.x))
    ({ var v = 0; v += 1; base?>x })?>x should be({ var v = 0; v += 1; base.map(_.x) }.map(_.x))
    base.func(base?>x)?>func(base?>x) should be(base.func(base.map(_.x)).map(_.func(base.map(_.x))))
  }

  "Option" should "work" in {
    val foo: Option[Foo] = Some(Foo(10))

    (foo?>ident: Option[Foo]) should be(Some(Foo(10)))
    (foo??>opt: Option[Foo]) should be(Some(Foo(10)))
    (foo??>ident.ident.opt: Option[Foo]) should be(Some(Foo(10)))
    (foo?>ident: Option[Foo]) should be(Some(Foo(10)))
    (foo??>ident.opt?>ident: Option[Foo]) should be(Some(Foo(10)))
    (foo??>generic[String].opt?>ident: Option[Foo]) should be(Some(Foo(10)))
    (foo??>func(5).opt?>ident: Option[Foo]) should be(Some(Foo(5)))
    (foo??>genericFunc[String](6).opt?>ident: Option[Foo]) should be(Some(Foo(6)))
  }

  "List" should "work" in {
    List("A", "B", "C")?>toLowerCase should be(List("a", "b", "c"))
  }
}
