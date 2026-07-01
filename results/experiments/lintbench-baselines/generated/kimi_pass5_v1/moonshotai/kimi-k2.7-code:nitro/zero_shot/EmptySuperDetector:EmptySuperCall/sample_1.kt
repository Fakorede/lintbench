package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.AbstractUastVisitor
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.UastCallKind

class EmptySuperDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(method: UMethod) {
                val emptySuperMethods = method.findSuperMethods().filter { hasEmptySuperAnnotation(it) }
                if (emptySuperMethods.isEmpty()) return

                val body = method.uastBody ?: return
                body.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(call: UCallExpression): Boolean {
                        if (call.receiver is USuperExpression && call.kind == UastCallKind.METHOD_CALL) {
                            val resolved = call.resolve()
                            val matches = if (resolved != null) {
                                emptySuperMethods.any { it == resolved }
                            } else {
                                call.methodName == method.name
                            }

                            if (matches) {
                                context.report(
                                    ISSUE,
                                    call,
                                    context.getLocation(call),
                                    "Calling super.${method.name}() is not recommended because the super method is annotated @EmptySuper"
                                )
                            }
                        }
                        return super.visitCallExpression(call)
                    }
                })
            }
        }
    }

    private fun hasEmptySuperAnnotation(method: PsiMethod): Boolean {
        return method.annotations.any {
            val name = it.qualifiedName ?: return@any false
            name == "EmptySuper" || name.endsWith(".EmptySuper")
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                Methods annotated with @EmptySuper indicate that overriding methods should not call the
                super implementation, either because it is empty or because it contains code not intended
                to be run when the method is overridden.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(EmptySuperDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}