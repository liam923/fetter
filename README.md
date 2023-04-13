# Fetter

Fetter is a compiler plugin for Scala 2 that brings optional chaining to Scala.
It allows you to write code like the following:
```scala
trait Person {
  val name: String
  val parent: Option[Person]
  val relatives: List[Person]
}

def foo(person: Option[Person]) = {
  val name: Option[String] = person?>name
  // desugars to: person.map(_.name)
  val parent: Option[Person] = person??>parent
  // desugars to: person.flatMap(_.name)
  val grandparentName: Option[String] = person??>parent??>parent?>name
  // desugars to: person.flatMap(_.parent.flatMap(_.parent.map(_.name)))
}
```

In fact, it works on any object for which `map` and `flatMap` are defined:
```scala
def getNames(people: List[Person]): List[String] = people?>name
def getRelatives(people: List[Person]): List[Person] = people??>relatives

val loadPerson: IO[Person] = ???
val loadName: IO[String] = loadPerson?>name
```

In general, the rule is that `?>` desugars to `map`, and `??>` desugars to `flatMap`.

## What is Optional Chaining?

Optional chaining in a powerful syntactic feature found in some languages, such as Swift, Kotlin, JavaScript, and TypeScript.
In these languages, when selecting from a nullable object, you may use `?.`.
If the object is null, the expression short-circuits and returns `null`.
For example, you may write `foo?.bar` if `foo` is nullable.
If `foo` is null, `foo?.bar` will be null, but if not, `foo?.bar` returns the same as `foo.bar` would.

For more information, see:
 - [Kotlin](https://kotlinlang.org/docs/null-safety.html#safe-calls)
 - [Swift](https://developer.apple.com/documentation/swift/optional)
 - [JavaScript](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/Optional_chaining)
 - [TypeScript](https://www.typescriptlang.org/docs/handbook/release-notes/typescript-3-7.html#optional-chaining)

## Using the Plugin

To use the plugin, first you must add the following external resolver to your `build.sbt``:
```scala
externalResolvers += "fetter resolver" at "https://maven.pkg.github.com/liam923/fetter"
```
Then, add the compiler plugin to the `build.sbt` file:
```scala
addCompilerPlugin("fetter" %% "fetter" % "1.0.0")
```
