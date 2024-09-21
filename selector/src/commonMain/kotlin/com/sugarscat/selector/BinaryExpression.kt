package com.sugarscat.selector

import kotlin.js.JsExport

@JsExport
data class BinaryExpression(
    override val start: Int,
    override val end: Int,
    val left: com.sugarscat.selector.ValueExpression,
    val operator: com.sugarscat.selector.PositionImpl<com.sugarscat.selector.CompareOperator>,
    val right: _root_ide_package_.com.sugarscat.selector.ValueExpression,
) : _root_ide_package_.com.sugarscat.selector.Expression() {
    override fun <T> match(
        context: _root_ide_package_.com.sugarscat.selector.Context<T>,
        transform: _root_ide_package_.com.sugarscat.selector.Transform<T>,
    ): Boolean {
        return operator.value.compare(context, transform, left, right)
    }

    override val binaryExpressions
        get() = arrayOf(this)

    override fun stringify() = "${left.stringify()}${operator.stringify()}${right.stringify()}"

    val properties: Array<String>
        get() = arrayOf(*left.properties, *right.properties)

    val methods: Array<String>
        get() = arrayOf(*left.methods, *right.methods)
}
