package dhall

import org.specs2.mutable._

import Expr._

class ExprSpec extends Specification {

  "shiftVariableIndices: should shift index if provided variable name matches with, " >> {
    "Lam domain label" >> {
      // \x: IntegerType -> x
      val given = Lam("x", IntegerType, Var("x", 0))
      val shifted = given.shiftVariableIndices(1, Var("x", 0))
      shifted mustEqual Lam("x", IntegerType, Var("x", 1))
    }

    "Let binding label" >> {
      // let x = 1 in x
      val given = Let("x", None, IntegerLit(1), Var("x", 0))
      val shifted = given.shiftVariableIndices(1, Var("x", 0))
      shifted mustEqual Let("x", None, IntegerLit(1), Var("x", 1))
    }

    "Var name" >> {
      // x
      val given = Var("x", 0)
      val shifted = given.shiftVariableIndices(1, Var("x", 0))
      shifted mustEqual Var("x", 1)
    }

    "Quant label" >> {
      // forall(a: Int) -> a
      val given = Quant("x", IntegerType, Var("x", 0))
      val shifted = given.shiftVariableIndices(1, Var("x", 0))
      shifted mustEqual Quant("x", IntegerType, Var("x", 1))
    }

    "Var in Lam in Let" >> {
      // let x = True in \x: BoolType -> x && True
      val given = Let(
        "x",
        Some(BoolType),
        BoolLit(true),
        Lam("x", BoolType, BoolAnd(Var("x", 0), BoolLit(true))))
      val shifted = given.shiftVariableIndices(1, Var("x", 0))
      val expected = Let(
        "x",
        Some(BoolType),
        BoolLit(true),
        Lam("x", BoolType, BoolAnd(Var("x", 1), BoolLit(true))))
      shifted mustEqual expected
    }
  }
  "substitute" >> {
    "should substitute variable with given expression in: " >> {
      "variable" >> {
        val given = Var("x", 0)
        val expr = Let("y", None, BoolLit(true), Var("y", 0))
        val substituted = given.substitute(Var("x", 0), expr)
        substituted mustEqual expr
      }

      "Let expression" >> {
        val given = Let("x", None, BoolLit(true), BoolAnd(Var("y", 0), Var("x", 0)))
        val substituted = given.substitute(Var("y", 0), BoolLit(true))
        substituted mustEqual Let("x", None, BoolLit(true), BoolAnd(BoolLit(true), Var("x", 0)))
      }

      "Lamda expression" >> {
        val given = Lam("x", BoolType, ListLit(None, Seq(Var("y", 0), BoolLit(false))))
        val substituted = given.substitute(Var("y", 0), BoolLit(true))
        substituted mustEqual Lam("x", BoolType, ListLit(None, Seq(BoolLit(true), BoolLit(false))))
      }
    }

    "should not substitute if same variable name is bound to: " >> {
      "Domain of lamda expression" >> {
        val given = Lam("x", BoolType, ListLit(None, Seq(Var("x", 0), BoolLit(false))))
        val substituted = given.substitute(Var("x", 0), BoolLit(true))
        substituted mustEqual given
      }

      "Let binding name" >> {
        val given =
          Let("x", Some(BoolType), BoolLit(true), ListLit(None, Seq(Var("x", 0), BoolLit(false))))
        val substituted = given.substitute(Var("x", 0), BoolLit(true))
        substituted mustEqual given
      }

      "Quant variable name" >> {
        val given = Quant("x", Const.Type, Var("x", 0))
        val substituted = given.substitute(Var("x", 0), Var("y", 0))
        substituted mustEqual given
      }
    }
  }

  "normalize" >> {
    "should normalize expression with " >> {
      "Literal bool ops " >> {
        val lit1 = BoolLit(true)
        val lit2 = BoolLit(false)
        "BoolAnd" >> { BoolAnd(lit1, lit2).normalize mustEqual lit2 }
        "BoolOr" >> { BoolOr(lit1, lit2).normalize mustEqual lit1 }
        "BoolNE" >> { BoolNE(lit1, lit2).normalize mustEqual lit1 }
        "BoolEQ" >> { BoolEQ(lit1, lit2).normalize mustEqual lit2 }
        "BoolIf" >> { BoolIf(lit1, IntegerLit(1), IntegerLit(2)).normalize mustEqual IntegerLit(1) }
      }

      "Literal natural ops " >> {
        val lit1 = NaturalLit(1)
        val lit2 = NaturalLit(2)
        "NaturalPlus" >> { NaturalPlus(lit1, lit2).normalize mustEqual NaturalLit(3) }
        "NaturalTimes" >> { NaturalTimes(lit1, lit2).normalize mustEqual NaturalLit(2) }
      }

      "Combine" >> {
        val innerRecord1 = RecordLit(Map("y1" -> NaturalPlus(Var("x", 0), NaturalLit(1))))
        val innerRecord2 = RecordLit(Map("y2" -> NaturalTimes(Var("x", 0), NaturalLit(2))))
        val expr1 = App(
          Lam("x", NaturalType, RecordLit(Map("x" -> Var("x", 0), "y" -> innerRecord1))),
          NaturalLit(1))
        val expr2 = App(
          Lam("x", NaturalType, RecordLit(Map("z" -> Var("x", 0), "y" -> innerRecord2))),
          NaturalLit(2))
        val combined = Combine(expr1, expr2).normalize
        combined mustEqual RecordLit(
          Map(
            "y" -> RecordLit(Map("y1" -> NaturalLit(2), "y2" -> NaturalLit(4))),
            "x" -> NaturalLit(1),
            "z" -> NaturalLit(2)))
      }

      "Merge" >> {
        val functions = RecordLit(
          Map(
            "Left" -> Lam("y", NaturalType, App(NaturalIsZero, Var("y", 0))),
            "Right" -> Lam(
              "x",
              BoolType,
              Let("x", Some(BoolType), BoolLit(true), BoolAnd(Var("x", 0), BoolLit(true))))
          ))

        val union1 = UnionLit("Left", NaturalLit(0), Map("Right" -> BoolType))
        val union2 = UnionLit("Right", BoolLit(false), Map("Left" -> NaturalType))

        Merge(functions, union1, BoolType).normalize mustEqual BoolLit(true)
        Merge(functions, union2, BoolType).normalize mustEqual BoolLit(true)
      }

      "Field" >> {
        val record = RecordLit(Map("x" -> NaturalLit(1), "y" -> NaturalLit(2)))
        val existing = "y"
        val nonExisting = "z"

        Field(record, existing).normalize mustEqual NaturalLit(2)
        Field(record, nonExisting).normalize mustEqual Field(record, nonExisting)
      }

      "App " >> {
        "Lamda" >> {
          App(Lam("x", NaturalType, NaturalTimes(Var("x", 0), Var("x", 0))), NaturalLit(2)).normalize mustEqual NaturalLit(
            4)
        }

        "ListBuild" >> {
          val ls =
            App(
              App(ListBuild, NaturalType),
              Lam(
                "l",
                Const.Type,
                Lam(
                  "cons",
                  Quant("_", NaturalType, Quant("_", Var("l", 0), Var("l", 0))),
                  Lam("nil", Var("l", 0), App(App(Var("cons", 0), NaturalLit(1)), Var("nil", 0))))
              )
            )

          ls.normalize mustEqual ListLit(Some(NaturalType), Seq(NaturalLit(1)))
        }

        "ListFold" >> {
          val fold =
            App(
              App(
                App(
                  App(ListFold, NaturalType),
                  ListLit(Some(NaturalType), Seq(1, 2, 3, 4).map(NaturalLit(_)))),
                Lam("x", NaturalType, Lam("y", NaturalType, NaturalPlus(Var("x", 0), Var("y", 0))))
              ),
              NaturalLit(0)
            ).normalize

          fold mustEqual NaturalLit(10)
        }

        "ListReverse" >> {
          val reverseExpr =
            App(App(ListReverse, NaturalType), ListLit(None, Seq(1, 2, 3).map(NaturalLit(_))))
          reverseExpr.normalize mustEqual ListLit(
            Some(NaturalType),
            Seq(3, 2, 1).map(NaturalLit(_)))
        }

        "ListHead" >> {
          val headExpr =
            App(App(ListHead, NaturalType), ListLit(None, Seq(1, 2).map(NaturalLit(_))))
          headExpr.normalize mustEqual OptionalLit(NaturalType, Seq(NaturalLit(1)))
        }

        "ListLast" >> {
          val lastExpr =
            App(App(ListLast, NaturalType), ListLit(None, Seq(1, 2).map(NaturalLit(_))))
          lastExpr.normalize mustEqual OptionalLit(NaturalType, Seq(NaturalLit(2)))
        }

        "ListLength" >> {
          val lengthExpr =
            App(App(ListLength, NaturalType), ListLit(None, Seq(1, 2).map(NaturalLit(_))))
          lengthExpr.normalize mustEqual NaturalLit(2)
        }

        "ListIndexed" >> {
          val lengthExpr =
            App(App(ListIndexed, NaturalType), ListLit(None, Seq(1, 2).map(NaturalLit(_))))
          val record = Record(Map("index" -> NaturalType, "value" -> NaturalType))
          val expected = ListLit(
            Some(record),
            List(
              RecordLit(Map("index" -> NaturalLit(0), "value" -> NaturalLit(1))),
              RecordLit(Map("index" -> NaturalLit(1), "value" -> NaturalLit(2)))))
          lengthExpr.normalize mustEqual expected
        }

        "NaturalFold" >> {
          val fold =
            App(
              App(
                App(App(NaturalFold, NaturalLit(2)), NaturalType),
                Lam("x", NaturalType, NaturalPlus(Var("x", 0), NaturalLit(15)))),
              NaturalLit(1))
          fold.normalize mustEqual NaturalLit(31)
        }

        "NaturalBuild" >> {
          val ls = App(
            NaturalBuild,
            Lam(
              "natural",
              Const.Type,
              Lam(
                "succ",
                Quant("_", Var("natural", 0), Var("natural", 0)),
                Lam(
                  "zero",
                  Var("natural", 0),
                  App(
                    Var("succ", 0),
                    App(Var("succ", 0), App(Var("succ", 0), App(Var("succ", 0), Var("zero", 0))))))
              )
            )
          )

          ls.normalize mustEqual NaturalLit(4)
        }

        "NaturalIsZero" >> {
          App(NaturalIsZero, NaturalLit(0)).normalize mustEqual BoolLit(true)
          App(NaturalIsZero, NaturalLit(1)).normalize mustEqual BoolLit(false)
        }
        "NaturalEven" >> {
          App(NaturalEven, NaturalLit(2)).normalize mustEqual BoolLit(true)
          App(NaturalEven, NaturalLit(3)).normalize mustEqual BoolLit(false)
        }
        "NaturalOdd" >> {
          App(NaturalOdd, NaturalLit(2)).normalize mustEqual BoolLit(false)
          App(NaturalOdd, NaturalLit(3)).normalize mustEqual BoolLit(true)
        }

        "OptionalFold" >> {
          def foldApplication(value: Seq[Expr[String, Int]]) =
            App(
              App(
                App(
                  App(App(OptionalFold, NaturalType), OptionalLit(NaturalType, value)),
                  NaturalType),
                Lam("x", NaturalType, Var("x", 0))),
              NaturalLit(0))

          "some case" >> {
            val optionalFold = foldApplication(Seq(NaturalLit(1)))
            optionalFold.normalize mustEqual NaturalLit(1)
          }

          "none case" >> {
            foldApplication(Nil).normalize mustEqual NaturalLit(0)
          }
        }
      }
    }
  }
}
