package asciibot.parser

import java.io.BufferedReader

import scala.collection.mutable.MutableList
import scala.io.Source
import picolib.maze.Maze
import picolib.semantics._

case class BlockError(m: String, sc: Int, ec: Int, line: Int) extends RuntimeException
case class LineError(m: String, line: Int) extends RuntimeException

object AsciibotParser {

  //// Error if rules are separated incorrectly (not with 3 vertical '|'s)
  //case class RuleSeparatorError(colNum: Int) extends ParserError
  //// Error if a state name is bad
  //case class StateNameError(stateName: String) extends ParserError
  // Generic syntax error

  def parseFile(f: String) : List[Rule] = {
    val lines: MutableList[String]= new MutableList
    val rules: MutableList[Rule] = new MutableList
    Source.fromFile(f).getLines().zipWithIndex foreach { case (line, lineNum) =>  
      if (line.equals("")) {
        if (lines.length > 0)
          throw new LineError("Blank line in middle of rule", lineNum)
      } else {
        lines += line
        if (lines.length == 3) {
          try {
            rules ++= parseLine(lines)
          } catch {
            case BlockError(m, sc, ec, _) => throw new BlockError(m, sc, ec, lineNum)
          }
          lines.clear
        }
      }
    }
    rules.toList
  }

  def parseLine(lns: Seq[String]) : List[Rule] = {
    assert(lns.length == 3)
    val maxLen = (lns map (l => l.length)).max
    // Make sure each string is the same length
    val nlns = lns map (_.padTo(maxLen, ' '))

    val rules: MutableList[Rule] = new MutableList

    var ruleStart = 0

    // iterate over columns
    var i = 0
    for (i <- 0 until maxLen) {
      // Number of bars in a colum
      val numBars = (nlns map (l => l(i)) filter (_=='|')).length
      if (!(numBars == 0 || numBars == 3)) {
        //throw new RuleSeparatorError(i)
        throw new BlockError("Vertical bar unexpected", i,i+1, 0)
      }
      if (numBars == 3) {
        rules += parseRule(nlns, ruleStart, i)
        ruleStart = i + 1
      }
    }
    rules += parseRule(nlns, ruleStart, maxLen)

    rules.toList
  }

  def parseRule(lines: Seq[String], start: Int, end: Int) : Rule = {
    val arrowStart = lines(1).indexOfSlice("->", start)
    if (arrowStart < start || arrowStart >= end) {
      throw new BlockError("No arrow found", start, end, 0)
    }
    val (inStateStart, inStateEnd) = trimBlock(lines, start, arrowStart)
    val (outStateStart, outStateEnd) = trimBlock(lines, arrowStart + 2, end) 
    val (surrs, inState) = parseInState(lines, inStateStart, inStateEnd)
    val (md, outState) = parseOutState(lines, outStateStart, outStateEnd)
    Rule(inState, surrs, md, outState)
  }

  def trimBlock(lines: Seq[String], start: Int, end: Int) : (Int, Int) = {
    var s = start
    var e = end
    while (lines(0)(s) == ' ' && lines(1)(s) == ' ' && lines(2)(s) == ' ') {
      s += 1;
    }
    while (lines(0)(e - 1) == ' ' && lines(1)(e - 1) == ' ' && lines(1)(e - 1) == ' ') {
      e -= 1;
    }
    (s, e)
  }

  def parseInState(a: Seq[String], start: Int, end: Int) : (Surroundings, State) = {
    assert(a.length == 3)
    val state_str = a(1).slice(start+1,end-1)
    // Check to make sure there are no illegal characters in the state name
    if ("x*o|-> " exists (state_str contains _)) {
      //throw new StateNameError(state_str)
      throw new BlockError(s"Bad state name `$state_str'", start, end, 0)
    }

    if (   a(0)(start) != ' '
        || a(0)(end-1) != ' '
        || a(2)(start) != ' '
        || a(2)(end-1) != ' ') {
        throw new BlockError("Corners of state box not empty", start, end, 0)
    }

    // NEWS order, strings
    val str_surrs = List(
      ("Top", a(0).slice(start,end) filter (_ != ' ')), // TODO: change to partition or something
      ("Right", a(1)(end-1).toString),
      ("Left", a(1)(start).toString),
      ("Bottom", a(2).slice(start,end)  filter (_ != ' '))
    )

    val surrs = str_surrs map (_ match {
        case (_, "x") => Blocked
        case (_, "o") => Open
        case (_, "*") => Anything
        case (dir, s) => throw new BlockError(s"Invalid Surrounding `$s' for $dir side", start, end, 0)
      })

    (Surroundings(surrs(0), surrs(1), surrs(2), surrs(3)), State(state_str))
  }

  def parseOutState(a: Seq[String], start: Int, end: Int) : (MoveDirection, State) = {
    //println(s"parseOutState: $a")
    assert(a.length == 3)
    if ("xo*" contains a(1)(start)) {
      if ("xo*" contains a(1)(end-1)) {
        // search each row's middle column for special characters.
        val moveTop = !("x*o|-> " exists { a(0).slice(start+1,end-1) contains _ })
        val noMove  = !("x*o|-> " exists { a(1).slice(start+1,end-1) contains _ })
        val moveBot = !("x*o|-> " exists { a(2).slice(start+1,end-1) contains _ })
        val moveRows = List(moveTop, noMove, moveBot).zipWithIndex
                      .filter{ case (bool, _) => bool }
                      .map{case (bool, i) => i};
        // If multiple matches, then which way to move is ambiguous
        if (moveRows.length != 1) {
          throw new BlockError(s"Move direction is ambiguous", start, end, 0)
        }
        // The row without any special characters is the direction we move
        val moveRow = moveRows(0)
        val moveDir = moveRow match {
          case 0 => North
          case 1 => StayHere
          case 2 => South
        }
        (moveDir, State(a(moveRow).slice(start+1,end-1)))
      } else { // move direction is to the right!
        // Check for errors
        (East, State(a(1).slice(start+2,end)))
      }
    } else { // move direction is left
      // Check for errors
      (West, State(a(1).slice(start, end-2)))
    }
  }
}