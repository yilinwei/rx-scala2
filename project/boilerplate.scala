import sbt.Keys._
import sbt.{IO, _}

case class Interface(pkg: String, name: String, pArity: Int, fixedReturn: Option[String], method: String) {
  def fullName: String = s"$pkg.$name"

  def arity = fixedReturn match {
    case None => 1 + pArity
    case Some(_) => pArity
  }
}

object Packages {
  val rx = "io.reactivex.functions"
  val util = "java.util.concurrent"
}

import Packages._

object Interfaces {

  val action = Interface(rx, "Action", 0, Some("Unit"), "run")
  val callable = Interface(util, "Callable", 0, None, "call")
  val function = Interface(rx, "Function", 1, None, "apply")
  val consumer = Interface(rx, "Consumer", 1, Some("Unit"), "accept")
  val predicate = Interface(rx, "Predicate", 1, Some("Boolean"), "test")
  val bifunction = Interface(rx, "BiFunction", 2, None, "apply")
  val biConsumer = Interface(rx, "BiConsumer", 2, Some("Unit"), "accept")
  val biPredicate = Interface(rx, "BiPredicate", 2, Some("Boolean"), "test")
  val function3 = Interface(rx, "Function3", 3, None, "apply")
  val function4 = Interface(rx, "Function4", 4, None, "apply")
  val function5 = Interface(rx, "Function5", 5, None, "apply")
  val function6 = Interface(rx, "Function6", 6, None, "apply")
  val function7 = Interface(rx, "Function7", 7, None, "apply")
  val function8 = Interface(rx, "Function8", 8, None, "apply")
  val function9 = Interface(rx, "Function9", 9, None, "apply")
}

case class Conversion(interface: Interface, fix: Option[String] = None)

trait Generator {
  def `A-Z`(arity: Int): String = if (arity == 0) "" else ('A' to `AZ`(arity - 1)).mkString(", ")
  def `AZ`(index: Int): Char = ('A'.toInt + index).toChar
  def `az`(index: Int): Char = ('a'.toInt + index).toChar
}

object ConversionGenerator extends Generator {

  def parameters(arity: Int): String = if (arity == 0) ""
  else (0 until arity)
    .map(index => s"${`az`(index)}: ${`AZ`(index)}")
    .mkString(", ")

  def `a-z`(arity: Int): String = if (arity == 0) "" else ('a' to `az`(arity - 1)).mkString(", ")

  def functionTpe(arity: Int): String = s"(${`A-Z`(arity)}) => ${`AZ`(arity)}"

  def conversion(version: String, interface: Interface, arity: Int): String = {

    val iTpe = if (interface.arity != 0) s"${interface.fullName}[${`A-Z`(interface.arity)}]" else s"${interface.fullName}"
    val fTpe = interface.fixedReturn match {
      case Some(returnTpe) => s"(${`A-Z`(arity)}) => $returnTpe"
      case None => functionTpe(arity)
    }
    val body = if (version.startsWith("2.12")) {
      s"""function => (${`a-z`(interface.pArity)}) => function(${`a-z`(interface.pArity)})"""
    } else {
      s"""
         | new Conversion[$fTpe, $iTpe] {
         |    def apply(function: $fTpe): $iTpe = new $iTpe {
         |      def ${interface.method}(${parameters(interface.pArity)}): ${interface.fixedReturn.getOrElse(`AZ`(arity))} =
         |        function(${`a-z`(arity)})
         |    }
         | }
       """
    }
    s"""implicit def function${arity}ToRx${interface.name}Conversion${if (interface.arity == 0) "" else s"[${`A-Z`(interface.arity)}]"}: Conversion[$fTpe, $iTpe] =
        |  $body
         """.stripMargin
  }

  def conversions(arity: Int, interfaces: Seq[Interface]): (String, File) => File = {
    (version, base) => {
      val fTpe = functionTpe(arity)
      val text =
        s"""
           | package io.rx
           | private[rx] trait Function${arity}Conversions {
           |    ${interfaces.map(conversion(version, _, arity)).mkString(System.lineSeparator())}
           | }
           |
       """.stripMargin
      val file = base / s"function${arity}RxJavaConversions.scala"
      IO.write(file, text)
      file
    }
  }

  def generate(config: (Int, Seq[Interface])*) = Def.task {
    val path = (sourceManaged in Compile).value / "rx" / "scala"
    val version = scalaVersion.value
    val conversionTraits = config.indices.map(arity => s"Function${arity}Conversions").mkString(" with ")
    val text =
      s"""
         | package io.rx
         | private[rx] trait FunctionConversions extends $conversionTraits
   """.stripMargin
    val file = path / "FunctionConversions.scala"
    IO.write(file, text)
    config.map {
      case (arity, interfaces) => conversions(arity, interfaces)(version, path)
    } :+ file
  }

}

object KConvertGenerator extends Generator {

  def kind(arity: Int): String = (0 until arity).map(_ => "_").mkString(",")

  def kConvert(arity: Int): String = {
    val name = s"KConvert${arity}Ops"
    val k = s"FF[${kind(arity)}]"
    s"""
       | implicit final class $name[$k, ${`A-Z`(arity)}](val value: FF[${`A-Z`(arity)}]) extends AnyVal {
       |    def convert[OO](implicit convert: Conversion[FF[${`A-Z`(arity)}], OO]) = convert(value)
       |    def convertK[GG[${kind(arity)}]](implicit convert: Conversion[FF[${`A-Z`(arity)}], GG[${`A-Z`(arity)}]]) = convert(value)
       |    ${if(arity > 1) s"def convertK1[GG[${kind(arity - 1)}]](implicit convert: Conversion[FF[${`A-Z`(arity)}], GG[${`A-Z`(arity - 1)}]]) = convert(value)" else ""}
       | }
     """.stripMargin
  }

  def generate(arity: Int) = Def.task {
    val path = (sourceManaged in Compile).value / "io" / "rx"
    val text = s"""
       | package io.rx
       | object KConvert {
       |  ${(1 until arity).map(kConvert).mkString(System.lineSeparator())}
       | }
     """.stripMargin
    val file = path / "KConvert.scala"
    IO.write(file, text)
    Seq(file)
  }
}
