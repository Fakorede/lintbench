package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiPrimitiveType
import org.jetbrains.uast.*

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        private val SUPER_CLASSES = listOf(
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "androidx.recyclerview.widget.DiffUtil.Callback",
            "android.support.v7.util.DiffUtil.ItemCallback",
            "android.support.v7.util.DiffUtil.Callback"
        )

        private val IMPLEMENTATION = Implementation(
            DiffUtilDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to decide whether two items represent the same content.
                Using reference equality (`==` in Java, `===` in Kotlin) or calling `equals()` on a type that has not overridden `equals()` can produce incorrect diff results and visual glitches.
                Compare the actual contents of the items instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )
    }

    override fun applicableSuperClasses(): List<String>? = SUPER_CLASSES

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (method in declaration.methods) {
            if (method.name != "areContentsTheSame") continue
            method.uastBody?.accept(
                object : org.jetbrains.uast.visitor.AbstractUastVisitor() {
                    override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                        this@DiffUtilDetector.visitBinaryExpression(context, node)
                        return super.visitBinaryExpression(node)
                    }

                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        this@DiffUtilDetector.visitCallExpression(context, node)
                        return super.visitCallExpression(node)
                    }
                }
            )
        }
    }

    override fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        if (!isInAreContentsTheSame(context, node)) return

        val operator = node.operator
        if (operator != UastBinaryOperator.IDENTITY_EQUALS &&
            operator != UastBinaryOperator.IDENTITY_NOT_EQUALS
        ) {
            return
        }

        val left = node.leftOperand.getExpressionType()
        val right = node.rightOperand.getExpressionType()
        if (left != null && right != null && left !is PsiPrimitiveType && right !is PsiPrimitiveType) {
            report(
                context, node,
                "Suspicious use of identity equality in `areContentsTheSame`; use `equals()` to compare item contents"
            )
        }
    }

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        if (!isInAreContentsTheSame(context, node)) return
        if (node.methodName != "equals" || node.valueArgumentCount != 1) return

        val resolved = node.resolve()
        val containingClass = resolved?.containingClass
        if (containingClass != null) {
            val qName = containingClass.qualifiedName
            if (qName == "java.lang.Object" || qName == "kotlin.Any") {
                report(
                    context, node,
                    "Calling `equals()` on a type that does not override `equals()` can produce incorrect DiffUtil results; compare the relevant fields instead"
                )
            }
            return
        }

        val receiverType = node.receiver?.getExpressionType() ?: return
        val receiverClass = (receiverType as? PsiClassType)?.resolve() ?: return
        if (!overridesEquals(receiverClass)) {
            report(
                context, node,
                "Calling `equals()` on a type that does not override `equals()` can produce incorrect DiffUtil results; compare the relevant fields instead"
            )
        }
    }

    private fun isInAreContentsTheSame(context: JavaContext, node: UElement): Boolean {
        val method = generateSequence(node.uastParent) { it.uastParent }
            .filterIsInstance<UMethod>()
            .firstOrNull()
        if (method?.name != "areContentsTheSame") return false

        return generateSequence(node.uastParent) { it.uastParent }
            .filterIsInstance<UClass>()
            .any { cls ->
                val psi = cls.javaPsi ?: return@any false
                SUPER_CLASSES.any { context.evaluator.extendsClass(psi, it, false) }
            }
    }

    private fun overridesEquals(cls: PsiClass): Boolean {
        for (method in cls.findMethodsByName("equals", true)) {
            val params = method.parameterList.parameters
            if (params.size != 1) continue
            if (params[0].type.canonicalText != "java.lang.Object") continue

            val owner = method.containingClass?.qualifiedName ?: continue
            if (owner != "java.lang.Object" && owner != "kotlin.Any") {
                return true
            }
        }
        return false
    }

    private fun report(context: JavaContext, node: UElement, message: String) {
        context.report(ISSUE, node, context.getLocation(node), message)
    }
}