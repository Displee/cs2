package org.runestar.cs2.ir

import org.runestar.cs2.Loader
import org.runestar.cs2.Script
import org.runestar.cs2.Type
import org.runestar.cs2.util.Chain
import org.runestar.cs2.util.HashChain
import org.runestar.cs2.util.ListStack
import org.runestar.cs2.util.toUnsignedInt

internal class Interpreter(
        val scriptLoader: Loader<Script>,
        val paramTypeLoader: Loader<Type>
) {

    fun interpret(id: Int): Function {
        val script = checkNotNull(scriptLoader.load(id))
        return makeFunction(id, script, interpretInstructions(script))
    }

    private fun interpretInstructions(script: Script): Array<Instruction> {
        val state = State(scriptLoader, paramTypeLoader, script)
        return Array(script.opcodes.size) {
            val insn = Op.translate(state)
            state.pc++
            if (insn !is Instruction.Assignment) {
                check(state.stack.isEmpty())
            }
            insn
        }
    }

    private fun makeFunction(id: Int, script: Script, instructions: Array<Instruction>): Function {
        val args = ArrayList<Element.Variable.Local>(script.intArgumentCount + script.stringArgumentCount)
        repeat(script.intArgumentCount) { args.add(Element.Variable.Local(it, Type.INT)) }
        repeat(script.stringArgumentCount) { args.add(Element.Variable.Local(it, Type.STRING)) }
        return Function(id, args, addLabels(instructions), script.returnTypes)
    }

    private fun addLabels(instructions: Array<Instruction>): Chain<Instruction> {
        val chain = HashChain<Instruction>()
        val labels = HashSet<Int>()
        for (insn in instructions) {
            when (insn) {
                is Instruction.Branch -> labels.add(insn.pass.id)
                is Instruction.Goto -> labels.add(insn.label.id)
                is Instruction.Switch -> insn.cases.values.mapTo(labels) { it.id }
            }
        }
        instructions.forEachIndexed { index, insn ->
            if (index in labels) {
                chain.addLast(Instruction.Label(index))
            }
            chain.addLast(insn)
        }
        return chain
    }

    internal class State(
            val scriptLoader: Loader<Script>,
            val paramTypeLoader: Loader<Type>,
            private val script: Script
    ) {

        var pc: Int = 0

        val stack: ListStack<StackValue> = ListStack()

        private var stackIdCounter: Int = 0

        val arrayTypes: Array<Type?> = arrayOfNulls(5)

        val opcode: Int get() = script.opcodes[pc].toUnsignedInt()

        val intOperand: Int get() = script.intOperands[pc]

        val stringOperand: String? get() = script.stringOperands[pc]

        fun operand(type: Type): Element.Constant = Element.Constant(if (type == Type.STRING) stringOperand else intOperand, type)

        val switch: Map<Int, Int> get() = script.switches[intOperand]

        fun peekValue(): Any? = stack.peek().value

        fun popValue(): Any? = stack.pop().value

        fun pop(type: Type): Element.Variable.Stack = stack.pop().toExpression(type)

        fun popAll(): List<Element.Variable.Stack> = stack.popAll().map { it.toExpression() }

        fun pop(count: Int): List<Element.Variable.Stack> = stack.pop(count).map { it.toExpression() }

        fun push(type: Type, value: Any? = null): Element.Variable.Stack {
            val v = StackValue(value, type, ++stackIdCounter)
            stack.push(v)
            return v.toExpression()
        }
    }
}