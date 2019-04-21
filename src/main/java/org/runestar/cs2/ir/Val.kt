package org.runestar.cs2.ir

import org.runestar.cs2.Type

internal data class Val(val cst: Any?, val type: Type, val id: Int) {

    fun toExpr(type: Type = this.type) = Expr.Variable.Stack(id, Type.bottom(type, this.type))

    override fun toString(): String = id.toString()
}