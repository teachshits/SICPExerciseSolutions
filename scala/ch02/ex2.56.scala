// Ex 2.56
// Represents expressions as lists of variables (Strings), numbers (Ints), and
// other expressions (List[Any]'s). 
// This version follows closely the Scheme and Clojure versions (which reflect
// the book...). Contrast with ex2.56.v2, which uses more idiomatic Scala, e.g.,
// objects.

type Expression = List[Any]
type Variable = String
type Number = Int
val Zero = 0
val One  = 1

private def toExpr(tokens: Any*) = tokens.size match {
  case 1 => tokens(0) match {
    case l:List[_] => l
    case x => List(x)
  }
  case _ => tokens.toList
}

private val ZeroExpr = toExpr(Zero)
private val OneExpr  = toExpr(One)

def isNumber (exp: Expression) = exp match {
  case head :: Nil => head match {
    case s:String => s matches """-?[\d\.]+"""
    case i:Int => true
    case _ => false
  }
  case _ => false
}

// Assumes isNumber returns true
def toNumber (exp: Expression) = exp.head match {
  case s:String => Integer.parseInt(s)
  case i:Int => i
  case x => throw new RuntimeException("exp.head is not a string: "+x.toString)
}

def numberEq (exp: Expression, num: Number) =
  isNumber(exp) && toNumber(exp) == num
  
def isVariable (exp: Expression) = exp match {
  case head :: Nil => head match {
    case s:String => s matches """\D\w*"""
    case _ => false
  }
  case _ => false
}

// Assumes isVariable returns true
def toVariable (exp: Expression) = exp.head match {
  case s:String => s
  case x => throw new RuntimeException("exp.head is not a string: "+x.toString)
}
  
def isSameVariable (v1: Variable, v2: Variable) = v1 == v2
  
private def isArithmeticExpression (exp: Expression, operator: String) = exp match {
  case head::tail => head == operator
  case _ => false
}

def isSum (exp: Expression) = isArithmeticExpression(exp, "+")
  
// Assumes the expression contains at least one argument/operand
private def extractFirstArg (exp: Expression) = toExpr(exp.tail.head)

// Assumes the expression contains at least two arguments/operands
private def extractSecondArg(exp: Expression) = toExpr(exp.tail.tail.head) 

def addend (exp: Expression) = extractFirstArg(exp)
def augend (exp: Expression) = extractSecondArg(exp)

def makeSum (exp1: Expression, exp2: Expression) =
  if      (numberEq(exp1, Zero)) exp2
  else if (numberEq(exp2, Zero)) exp1
  else if (isNumber(exp1) && isNumber(exp2)) 
    toExpr(toNumber(exp1) + toNumber(exp2))
  else 
    toExpr("+", exp1, exp2)
    
def isDifference (exp: Expression) = isArithmeticExpression(exp, "-")

def minuend (exp: Expression) = extractFirstArg(exp)
def subtrahend (exp: Expression) = extractSecondArg(exp)
  
def makeDifference (exp1: Expression, exp2: Expression) =
  if      (isNumber(exp1) && isNumber(exp2)) 
    toExpr(toNumber(exp1) - toNumber(exp2))
  else if (numberEq(exp1, Zero))
    toExpr("-", Zero, exp2)
  else if (numberEq(exp2, Zero)) exp1
  else 
    toExpr("-", exp1, exp2)
  
def isProduct (exp: Expression) = isArithmeticExpression(exp, "*")

def multiplier (exp: Expression) = extractFirstArg(exp)  
def multiplicand (exp: Expression) = extractSecondArg(exp)
  
def makeProduct (exp1: Expression, exp2: Expression) =
  if      (numberEq(exp1, Zero) || numberEq(exp2, Zero)) ZeroExpr
  else if (numberEq(exp1, One)) exp2
  else if (numberEq(exp2, One)) exp1
  else if (isNumber(exp1) && isNumber(exp2))
    toExpr(toNumber(exp1) * toNumber(exp2))
  else 
    toExpr("*", exp1, exp2)

def isExponentiation (exp: Expression) = isArithmeticExpression(exp, "**")
  
def base (exp: Expression) = extractFirstArg(exp)  
def exponent (exp: Expression) = extractSecondArg(exp)
  
private def calcNumberExponential(base: Number, exponent: Number): Number =
  exponent match {
    case Zero => One
    case _ => base * calcNumberExponential(base, exponent - 1)
  }
  
def makeExponentiation (exp1: Expression, exp2: Expression): Expression =
  if      (numberEq(exp1, Zero)) ZeroExpr
  else if (numberEq(exp2, Zero)) OneExpr
  else if (numberEq(exp2, One))  exp1
  else if (isNumber(exp1) && isNumber(exp2))
    toExpr(calcNumberExponential(toNumber(exp1), toNumber(exp2)))
  else 
    toExpr("**", exp1, exp2)

// We use the parser combinator library to parse the expression strings into 
// Expression objects. 
// Actually, if we're doing this, we could just do the differentiation 
// as we go. However, it's usually better to separate parsing of expressions 
// from using 'em...
import scala.util.parsing.combinator._

// "wholeNumber" and "ident" supplied by JavaTokenParsers
object expressionParser extends JavaTokenParsers {
  def expression = parentheticalExpression | tokenAsList
  def exp: Parser[Any] = parentheticalExpression | token
  def parentheticalExpression = "(" ~> operator ~ operands <~ ")" ^^ { 
    case op ~ rands => op :: rands
  }
  def operands = exp ~ rep1(exp) ^^ { 
    case e ~ es => e :: es
  }
  def tokenAsList = token ^^ { t => List(t) }
  def token = ( number | variable )
  def number = wholeNumber ^^ { n => Integer.parseInt(n) }  
  def variable = ident
  def operator = sum | difference | exponentiation | product
  def sum = "+"
  def difference = "-"
  def exponentiation = "**"
  def product = "*"
}

implicit def expressionStringToExpression(exp: String): Expression = 
  expressionParser.parseAll(expressionParser.expression, exp) match {
    case expressionParser.Success(e,_) => e
    case x => throw new RuntimeException("expression parsing failed! "+x)
  }
  
// Convert an expression back into an sexp string.
private def stringize(exp: Any): String = exp match {
  case Nil => ""
  case head::Nil => stringize(head)  // suppress "(...)" for a 1-element list
  case head::tail => 
    String.format(
      "(%s)", (head::tail).map(stringize(_)).reduceLeft(_ + " " + _))
  case x => x.toString
}

def deriv (exp: Expression, variable: Variable): String = 
  stringize(computeDeriv(exp, variable))

private def computeDeriv (exp: Expression, variable: Variable): Expression = {
  if      (isNumber(exp)) ZeroExpr
  else if (isVariable(exp))
    if (isSameVariable(toVariable(exp), variable)) OneExpr else ZeroExpr
  else if (isSum(exp))
    makeSum (computeDeriv (addend(exp), variable),
             computeDeriv (augend(exp), variable))
  else if (isDifference(exp))
    makeDifference (computeDeriv (minuend(exp), variable),
                    computeDeriv (subtrahend(exp), variable))
  else if (isProduct(exp))
    makeSum (makeProduct (multiplier(exp),
                         (computeDeriv (multiplicand(exp), variable))),
             makeProduct (computeDeriv (multiplier(exp), variable),
                          multiplicand(exp)))
  else if (isExponentiation(exp))
    makeProduct (makeProduct (
                  exponent(exp),
                  makeExponentiation (
                    base(exp), 
                    makeDifference (exponent(exp), OneExpr))),
                 makeProduct (computeDeriv (base(exp), variable), OneExpr))
  else 
    throw new RuntimeException("unknown expression type -- DERIV: "+exp)
}

import org.scalatest._ 
import org.scalatest.matchers._

object derivSpec extends Spec with ShouldMatchers {
  def testMakeExponentiation(n1:Number, n2:Number, expected:Number) = 
    makeExponentiation (toExpr(n1), toExpr(n2)) should equal (toExpr(expected))
    
  describe ("makeExponentiation") {
    it ("should compute the correct expontential expressions") {
      testMakeExponentiation (2, 0, 1)
      testMakeExponentiation (2, 1, 2)
      testMakeExponentiation (2, 2, 4)
      testMakeExponentiation (2, 3, 8)
      testMakeExponentiation (2, 4, 16)
    }
  }
  describe ("expressionParser") {
    it ("should parse expression strings into list trees") {
      expressionStringToExpression("1")  should equal (List(1))
      expressionStringToExpression("x")  should equal (List("x"))
      expressionStringToExpression("(+  1 2)")  should equal (List("+",  1, 2))
      expressionStringToExpression("(-  1 2)")  should equal (List("-",  1, 2))
      expressionStringToExpression("(*  1 2)")  should equal (List("*",  1, 2))
      expressionStringToExpression("(** 1 2)")  should equal (List("**", 1, 2))
      expressionStringToExpression("(+ x 3)") should equal (List("+", "x", 3))
      expressionStringToExpression("(* (* x y) (- x 3))") should equal (
        List("*", List("*", "x", "y"), List("-", "x", 3)))
    }
  }
  describe ("deriv") {
    it ("should compute the correct differentation expressions") {
      deriv ("(+ x 3)", "x") should equal ("1")
      deriv ("(- x 3)", "x") should equal ("1")
      deriv ("(* x y)", "x") should equal ("y")
      deriv ("(* (* x y) (+ x 3))", "x") should equal ("(+ (* x y) (* y (+ x 3)))")
      deriv ("(* (* x y) (- x 3))", "x") should equal ("(+ (* x y) (* y (- x 3)))")
      
      deriv ("(** x 1)", "x") should equal ("1")
      deriv ("(** x 2)", "x") should equal ("(* 2 x)")
      deriv ("(** x 3)", "x") should equal ("(* 3 (** x 2))")
      deriv ("(** x 4)", "x") should equal ("(* 4 (** x 3))")
      deriv ("(** x n)", "x") should equal ("(* n (** x (- n 1)))")
    }
  }
}
derivSpec execute