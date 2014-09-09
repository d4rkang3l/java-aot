import java.io.{File, FileInputStream}

import org.objectweb.asm.tree._
import org.objectweb.asm.{Label, ClassReader, Opcodes, Type}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object Main extends App {
  val result = Test1.sum(1, 2)
  println(Test1.doFor2(0, 10))

  //handleClass(new File("target/classes/Main.class"))
  handleClass(new File("target/classes/Test1.class"))

  def handleClass(file: File) = {
    val cr = new ClassReader(new FileInputStream(file))
    val cn = new ClassNode()
    cr.accept(cn, 0)
    for (_method <- cn.methods.asScala) {
      val method = _method.asInstanceOf[MethodNode]
      println("-----------------------")
      println(s"${method.name}_${method.desc}")
      handleMethod(cn, method)
    }
  }

  def handleMethod(classNode:ClassNode, method: MethodNode) = {
    var statements = new StmList()
    var stack = new mutable.Stack[Expr]()
    var tempindex = 0

    if (method.instructions.size > 0) {
      for (_instruction <- method.instructions.iterator().asScala) {
        //println(_instruction)

        //val instruction = _instruction.asInstanceOf[AbstractInsnNode]
        _instruction match {
          case field: FieldInsnNode =>
            field.getOpcode match {
              case Opcodes.GETSTATIC =>
                //throw new Exception("Not implemented GETSTATIC")
                stack.push(new FieldAccessExpr(new TypeRefExpr(field.owner), field.name))

              case Opcodes.PUTSTATIC =>
                val value = stack.pop()
                statements.nodes.append(new AssignStm(new FieldAccessExpr(new TypeRefExpr(field.owner), field.name), value))

              case Opcodes.GETFIELD =>
                val ref = stack.pop()
                stack.push(new FieldAccessExpr(ref, field.name))

              case Opcodes.PUTFIELD =>
                val ref = stack.pop()
                val value = stack.pop()
                statements.nodes.append(new AssignStm(new FieldAccessExpr(ref, field.name), value))
            }
          //println(s"FIELD: ${field.owner}.${field.name} :: ${field.desc}")

          case method: MethodInsnNode =>
            var isStatic = false
            method.getOpcode match {
              case Opcodes.INVOKEVIRTUAL => isStatic = false
              case Opcodes.INVOKESPECIAL => isStatic = false
              case Opcodes.INVOKESTATIC => isStatic = true
              case Opcodes.INVOKEINTERFACE => isStatic = false
            }
            val methodType = Type.getMethodType(method.desc)
            val argumentTypes = methodType.getArgumentTypes
            val argumentExprs = (for (n <- argumentTypes) yield stack.pop()).reverse.toArray
            val thisExpr = if (!isStatic) {
              stack.pop()
            } else {
              new TypeRefExpr(method.owner)
            }
            stack.push(new MethodCall(method.owner, method.name, method.desc, thisExpr, argumentExprs.toArray))
            if (methodType.getReturnType.getSort == Type.VOID) {
              statements.nodes.append(new ExprStm(stack.pop()))
            }

          //methodType.getReturnType
          //method.desc
          //method.itf
          //println(s"CALL: ${method.owner}.${method.name} :: ${method.desc}")

          case frame: FrameNode =>
            println("frame!!")

            frame.`type` match {
              case Opcodes.F_NEW =>
              case Opcodes.F_FULL =>
              case Opcodes.F_APPEND =>
              case Opcodes.F_CHOP =>
              case Opcodes.F_SAME =>
              case Opcodes.F_SAME1 =>
            }

            /*
            println(frame.`type`)
            println(frame.local)
            println(frame.stack)
            */

          case iinc:IincInsnNode =>
            statements.nodes.append(new AssignStm(new VarExpr(iinc.`var`), new BinOp(new VarExpr(iinc.`var`), new ConstExpr(1), "+")))

          case varn: VarInsnNode =>
            var loading = false
            varn.getOpcode match {
              case Opcodes.ILOAD | Opcodes.LLOAD | Opcodes.FLOAD | Opcodes.DLOAD | Opcodes.ALOAD => loading = true
              case Opcodes.ISTORE | Opcodes.LSTORE | Opcodes.FSTORE | Opcodes.DSTORE | Opcodes.ASTORE => loading = false
              case Opcodes.RET => throw new Exception("Not supported RET");
            }
            if (loading) {
              stack.push(new VarExpr(varn.`var`))
            } else {
              statements.nodes.append(new AssignStm(new VarExpr(varn.`var`), stack.pop()))
            }

          case linen: LineNumberNode =>
            statements.nodes.append(new LineNumberStm(linen.line))


          case typen: TypeInsnNode =>
            typen.getOpcode match {
              case Opcodes.NEW => stack.push(new NewExpr(typen.desc))
              case Opcodes.ANEWARRAY => stack.push(new NewArrayExpr(stack.pop(), typen.desc))
              case Opcodes.CHECKCAST => stack.push(new CheckCastExpr(stack.pop(), typen.desc))
              case Opcodes.INSTANCEOF => stack.push(new InstanceofExpr(stack.pop(), typen.desc))
            }

          case ldc:LdcInsnNode =>
            stack.push(new ConstExpr(ldc.cst))

          case label:LabelNode =>
            statements.nodes.append(new LabelStm(label.getLabel))

          case jump:JumpInsnNode =>
            statements.nodes.append(jump.getOpcode match {
              case Opcodes.IFEQ | Opcodes.IFNE | Opcodes.IFLT | Opcodes.IFGE | Opcodes.IFGT | Opcodes.IFLE =>
                val left = stack.pop()
                val right = new ConstExpr(0)
                val op = jump.getOpcode match {
                  case Opcodes.IFEQ => "=="
                  case Opcodes.IFNE => "!="
                  case Opcodes.IFLT => "<"
                  case Opcodes.IFGE => ">="
                  case Opcodes.IFGT => ">"
                  case Opcodes.IFLE => "<="
                }
                new JumpIfStm(new BinOp(left, right, op), jump.label.getLabel)

              case Opcodes.IF_ICMPEQ | Opcodes.IF_ICMPNE | Opcodes.IF_ICMPLT | Opcodes.IF_ICMPGE | Opcodes.IF_ICMPGT | Opcodes.IF_ICMPLE | Opcodes.IF_ACMPEQ | Opcodes.IF_ACMPNE =>
                val right = stack.pop()
                val left = stack.pop()
                val op = jump.getOpcode match {
                  case Opcodes.IF_ICMPEQ => "=="
                  case Opcodes.IF_ICMPNE => "!="
                  case Opcodes.IF_ICMPLT => "<"
                  case Opcodes.IF_ICMPGE => ">="
                  case Opcodes.IF_ICMPGT => ">"
                  case Opcodes.IF_ICMPLE => "<="
                  case Opcodes.IF_ACMPEQ => "=="
                  case Opcodes.IF_ACMPNE => "!="
                }
                new JumpIfStm(new BinOp(left, right, op), jump.label.getLabel)

              case Opcodes.GOTO => new JumpStm(jump.label.getLabel)
              case Opcodes.IFNULL => new JumpIfStm(new BinOp(stack.pop(), new ConstExpr(null), "=="), jump.label.getLabel)
              case Opcodes.IFNONNULL => new JumpIfStm(new BinOp(stack.pop(), new ConstExpr(null), "!="), jump.label.getLabel)

              case Opcodes.JSR => throw new Exception("Not implemented JSR");
            })

          case insn: InsnNode =>
            insn.getOpcode match {
              case Opcodes.RETURN =>
                statements.nodes.append(new ReturnStm(new VoidExpr))
              case Opcodes.LRETURN | Opcodes.IRETURN | Opcodes.ARETURN =>
                statements.nodes.append(new ReturnStm(stack.pop()))
              case
                Opcodes.IADD | Opcodes.ISUB | Opcodes.IMUL | Opcodes.IREM | Opcodes.IDIV
                | Opcodes.LADD | Opcodes.LSUB | Opcodes.LMUL | Opcodes.LREM | Opcodes.LDIV
                | Opcodes.FADD | Opcodes.FSUB | Opcodes.FMUL | Opcodes.FREM | Opcodes.FDIV
                | Opcodes.DADD | Opcodes.DSUB | Opcodes.DMUL | Opcodes.DREM | Opcodes.DDIV
              =>
                val right = stack.pop()
                val left = stack.pop()
                stack.push(new BinOp(left, right, insn.getOpcode match {
                  case Opcodes.IADD | Opcodes.LADD | Opcodes.FADD | Opcodes.DADD => "+"
                  case Opcodes.ISUB | Opcodes.LSUB | Opcodes.FSUB | Opcodes.DSUB => "-"
                  case Opcodes.IMUL | Opcodes.LMUL | Opcodes.FMUL | Opcodes.DMUL => "*"
                  case Opcodes.IREM | Opcodes.LREM | Opcodes.FREM | Opcodes.DREM => "%"
                  case Opcodes.IDIV | Opcodes.LDIV | Opcodes.FDIV | Opcodes.DDIV => "/"
                }))
              case Opcodes.ICONST_M1 => stack.push(new ConstExpr(-1))
              case Opcodes.ICONST_0 => stack.push(new ConstExpr(0))
              case Opcodes.ICONST_1 => stack.push(new ConstExpr(1))
              case Opcodes.ICONST_2 => stack.push(new ConstExpr(2))
              case Opcodes.ICONST_3 => stack.push(new ConstExpr(3))
              case Opcodes.ICONST_4 => stack.push(new ConstExpr(4))
              case Opcodes.ICONST_5 => stack.push(new ConstExpr(5))
              case Opcodes.AALOAD =>
                val index = stack.pop()
                val arrayref = stack.pop()
                stack.push(new ArrayAccessExpr(arrayref, index))
              case Opcodes.AASTORE =>
                val value = stack.pop()
                val index = stack.pop()
                val arrayref = stack.pop()
                statements.nodes.append(new AssignStm(new ArrayAccessExpr(arrayref, index), value))
              case Opcodes.DUP =>
                val v = stack.pop()
                val expr = new TempExpr(tempindex)
                stack.push(expr)
                stack.push(new AssignTemp(tempindex, v))
                tempindex += 1
              case Opcodes.I2L => stack.push(new CastExpr(stack.pop(), "s32", "s64"))
              case Opcodes.I2F => stack.push(new CastExpr(stack.pop(), "s32", "f32"))
              case Opcodes.I2D => stack.push(new CastExpr(stack.pop(), "s32", "f64"))

              case Opcodes.L2I => stack.push(new CastExpr(stack.pop(), "s64", "s32"))
              case Opcodes.L2F => stack.push(new CastExpr(stack.pop(), "s64", "f32"))
              case Opcodes.L2D => stack.push(new CastExpr(stack.pop(), "s64", "f64"))

              case Opcodes.F2I => stack.push(new CastExpr(stack.pop(), "f32", "s32"))
              case Opcodes.F2L => stack.push(new CastExpr(stack.pop(), "f32", "s64"))
              case Opcodes.F2D => stack.push(new CastExpr(stack.pop(), "f32", "f64"))

              case Opcodes.D2I => stack.push(new CastExpr(stack.pop(), "f64", "s32"))
              case Opcodes.D2L => stack.push(new CastExpr(stack.pop(), "f64", "s64"))
              case Opcodes.D2F => stack.push(new CastExpr(stack.pop(), "f64", "f32"))

              case Opcodes.I2B => stack.push(new CastExpr(stack.pop(), "s32", "s8"))
              case Opcodes.I2C => stack.push(new CastExpr(stack.pop(), "s32", "u16"))
              case Opcodes.I2S => stack.push(new CastExpr(stack.pop(), "s32", "s16"))

              case _ =>
                throw new Exception(s"Unhandled INSN ${insn.getOpcode}");
            }

          case _ =>
            throw new Exception(s"Unhandled instruction ${_instruction}");
        }
        //println(instruction)
      }
    }

    println(CppGenerator.generateMethod(classNode.name, method.name, method.desc, statements))
  }
}

object CppGenerator {
  def descToCType(jtype:Type):String = {
    jtype.getSort match {
      case Type.INT => "s32"
      case _ => "Unhandled_" + jtype.getDescriptor
    }
  }

  def generateMethod(className:String, methodName:String, methodDesc:String, node:Node): String = {
    var out = ""
    val methodType = Type.getMethodType(methodDesc)

    out += descToCType(methodType.getReturnType) + " " + className + "::" + methodName
    out += "(" + (for (arg <- methodType.getArgumentTypes) yield descToCType(arg)).mkString(", ") + ") "
    out += "{\n"
    out += generateCode(node)
    out += "}\n"
    out
  }

  def generateCode(node: Node): String = {
    node match {
      case expr: Expr =>
        expr match {
          case _return: VoidExpr => ""
          case varexpr: VarExpr => s"var_${varexpr.varIndex}"
          case tempExpr: TempExpr => s"temp_${tempExpr.index}"
          case classref: TypeRefExpr => classref.name
          case arraya:ArrayAccessExpr => generateCode(arraya.expr) + "[" + generateCode(arraya.index) + "]"
          case check:CheckCastExpr => "((" + check.desc + ")(" + generateCode(check.expr) + "))"
          case assignTemp:AssignTemp => "auto temp_" + assignTemp.index + " = " + generateCode(assignTemp.expr)
          case newArrayExpr:NewArrayExpr => "new " + newArrayExpr.desc + "[" + generateCode(newArrayExpr.countExpr) + "]"
          case newExpr:NewExpr => "(new " + newExpr.desc + "())"
          case binop:BinOp => "(" + generateCode(binop.left) + " " + binop.op + " " + generateCode(binop.right) + ")"
          case cast:CastExpr => "((" + cast.to + ")(" + generateCode(cast.expr) + "))"
          case const: ConstExpr =>
            const.value match {
              case string: String => "\"" + const.value.toString + "\""
              case _ => const.value.toString
            }
          case fieldaccess: FieldAccessExpr =>
            fieldaccess.base match {
              case classref: TypeRefExpr =>
                s"${classref.name}::${fieldaccess.fieldName}"
              case _ =>
                generateCode(fieldaccess.base) + "->" + fieldaccess.fieldName
            }

          case methodCall: MethodCall =>
            val methodExpr = methodCall.thisExpr match {
              case _:TypeRefExpr => generateCode(methodCall.thisExpr) + "::"
              case _ => generateCode(methodCall.thisExpr) + "->"
            }
            val methodName = methodCall.methodName
            val argsList = (for (arg <- methodCall.args) yield generateCode(arg)).mkString(", ")
            s"${methodExpr}${methodName}(${argsList})"

          case _ => node.toString
        }
      case stm: Stm =>
        stm match {
          case label:LabelStm => "label_" + label.label + ":;\n"
          case jump:JumpIfStm => "if (" + generateCode(jump.expr) + ") goto label_" + jump.label + ";\n"
          case jump:JumpStm => "goto label_" + jump.label + ";\n"
          case list: StmList => (for (node2 <- list.nodes) yield generateCode(node2)).mkString("")
          case assignStm: AssignStm => generateCode(assignStm.lvalue) + " = " + generateCode(assignStm.expr) + ";\n"
          case exprStm: ExprStm => generateCode(exprStm.expr) + ";\n"
          case _return: ReturnStm => s"return ${generateCode(_return.expr)};\n"
          //case linen: LineNumberStm => s"// line ${linen.line}\n"
          case linen: LineNumberStm => ""
          case _ => node.toString + "\n"
        }
      case _ => node.toString + "\n"
    }
  }
}

trait Node

class Expr extends Node
class VoidExpr extends Expr
class LValue extends Expr
class VarExpr(val varIndex: Int) extends LValue
class TempExpr(val index: Int) extends LValue
class FieldAccessExpr(val base: Expr, val fieldName: String, val fieldDesc: String = "") extends LValue
class TypeRefExpr(val name: String) extends Expr
class MethodCall(val className: String, val methodName: String, val methodType: String, val thisExpr: Expr, val args: Array[Expr]) extends Expr
class NewExpr(val desc:String) extends Expr
class NewArrayExpr(val countExpr:Expr, val desc:String) extends Expr
class CheckCastExpr(val expr:Expr, val desc:String) extends Expr
class InstanceofExpr(val expr:Expr, val desc:String) extends Expr
class ConstExpr(val value:Any) extends Expr
class ArrayAccessExpr(val expr:Expr, val index:Expr) extends LValue
class AssignTemp(val index:Int, val expr:Expr) extends Expr
class BinOp(val left:Expr, val right:Expr, val op:String) extends Expr
class CastExpr(val expr: Expr, val from: String, val to: String) extends Expr

class Stm extends Node
class ReturnStm(val expr: Expr) extends Stm
class ExprStm(val expr: Expr) extends Stm
class AssignStm(val lvalue: LValue, val expr: Expr) extends Stm
class StmList(val nodes: ListBuffer[Stm] = new ListBuffer[Stm]()) extends Stm
class LineNumberStm(val line:Int) extends Stm
class LabelStm(val label:Label) extends Stm
class JumpIfStm(val expr:Expr, val label:Label) extends Stm
class JumpStm(val label:Label) extends Stm