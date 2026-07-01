private fun isWindowStateChangedConstant(context: JavaContext, expression: UExpression?): Boolean {
    if (expression == null) return false

    val referenced = (expression as? UReferenceExpression)?.resolve()
    if (referenced is PsiField && referenced.name == "TYPE_WINDOW_STATE_CHANGED") {
        val cls = referenced.containingClass?.qualifiedName
        if (cls in TARGET_CLASSES) {
            return true
        }
    }

    val value = ConstantEvaluator().evaluate(expression)
    return value is Number && value.toInt() == TYPE_WINDOW_STATE_CHANGED_VALUE
}