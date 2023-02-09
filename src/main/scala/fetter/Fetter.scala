package fetter

import scala.annotation.tailrec
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.{Global, Phase}

/**
 * A compiler plugin that allows for optional chaining, such as in Swift and Kotlin.
 *
 * Expressions like `a??>b.c?>d` are translated into `a.flatMap(_.b.c.map(_.d))`, following the rule
 * that `??>` corresponds to `flatMap` and `?>` corresponds to `map`.
 */
class Fetter(val global: Global) extends Plugin {
  import global._

  val name = "fetter"
  val description = "allows for optional chaining syntax"
  val components = List[PluginComponent](Component)

  private object Component extends PluginComponent {
    val global: Fetter.this.global.type = Fetter.this.global
    val runsAfter = List[String]("parser")
    val phaseName = Fetter.this.name

    def newPhase(_prev: Phase) = new FetterPhase(_prev)

    /**
     * The custom compiler phase that does the transformation. It should be run before typing because the optional
     * chains will fail type checking before transformation.
     */
    class FetterPhase(prev: Phase) extends StdPhase(prev) {
      override def name = Fetter.this.name

      def apply(unit: CompilationUnit): Unit = {
        // Delegate the transformation of the code to ChainTransformer
        unit.body = unit.body.transform(ChainTransformer)
      }
    }
  }

  /**
   * This is where the magic happens. ChainTransformer implements [[Transformer]], which allows it to recur over a
   * [[Tree]] and modify it.
   */
  private object ChainTransformer extends Transformer {
    /**
     * Given a [[Tree]] that has an [[Ident]] as its base (such as `x.y.z`) and a [[Name]] (such as `w`), inserts
     * the name at the base of the tree (with the given examples, creating `w.x.y.z`). If there is not an [[Ident]]
     * at the base, `None` is returned.
     */
    private def insertParamAtBase(tree: Tree, param: Name): Option[Tree] = {
      tree match {
        case Ident(name) => Some(Select(Ident(param), name))
        case Select(base, name) =>
          insertParamAtBase(base, param).map { newBase =>
            Select(newBase, name)
          }
        case Apply(fun, args) =>
          insertParamAtBase(fun, param).map { newFun =>
            Apply(newFun, args)
          }
        case TypeApply(fun, args) =>
          insertParamAtBase(fun, param).map { newFun =>
            TypeApply(newFun, args)
          }
        case _ => None
      }
    }

    /**
     * If the tree is a optional chain, it will be converted into maps/flatmaps.
     *
     * `?>` and `??>` are left associative, which makes the recursion a bit weird.
     * An expression like `a?>b?>c` desugars to `a.?>(b).?>(c)` after parser.
     * `createChain` recurs left down the tree and continues until it reaches an expression not of the form
     * `<some-expression>.?>(<some-chain-of-selections>)`. While it recurs down, it holds `tail` as an accumulator.
     * `tail` is a function that given an expression, adds that accumulated chain onto the expression.
     */
    @tailrec
    private def createChain(tree: Tree, tail: Tree => Tree): Tree = {
      val actions = Map("$qmark$greater" -> "map", "$qmark$qmark$greater" -> "flatMap")

      tree match {
        case Apply(Select(base, TermName(funName)), List(arg)) if actions.contains(funName) =>
          val param = TermName("x$1")
          val valDef = ValDef(Modifiers(Flag.PARAM), param, TypeTree(), EmptyTree)

          insertParamAtBase(arg, param) match {
            case Some(newArg) =>
              val lambda = Function(List(valDef), tail(newArg))
              createChain(base, b => Apply(Select(b, actions(funName)), List(lambda)))
            case None => tail(tree).transform(this)
          }
        case tree => tail(tree).transform(this)
      }
    }

    override def transform(tree: Tree): Tree = {
      createChain(tree, tail = identity)
    }
  }
}
