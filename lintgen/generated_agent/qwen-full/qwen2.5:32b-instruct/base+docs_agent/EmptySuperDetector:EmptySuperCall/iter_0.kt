package com.android.tools.lint.checks

import com.android.SdkConstants.ANNOTATION_EMPTY_SUPER
import com.android.annotations.VisibleForTesting
import com.android.resources.ResourceFolderType
import com.intellij.psi.PsiMethodCallExpression
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.UastVisitor

class EmptySuperDetector : Detector(), SourceCodeScanner {

    companion object Issues {
        val EMPTY_SUPER_CALL = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                Methods annotated with `@EmptySuper` should not have their super implementation called when overridden.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                EmptySuperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    @VisibleForTesting
    internal fun isAnnotatedWithEmptySuper(method: UElement): Boolean {
        return method.annotations.any { it.qualifiedName == ANNOTATION_EMPTY_SUPER }
    }

    override fun visitNode(context: JavaContext, node: UElement) {
        if (node !is UMethod || context.evaluator.isAndroidProject()) {
            return
        }

        val overriddenMethods = context.evaluator.getOverriddenMethods(node)
        for (overridden in overriddenMethods) {
            if (isAnnotatedWithEmptySuper(overridden)) {
                node.accept(object : UastVisitor {
                    override fun visitMethodCallExpression(node: UMethodCallExpression): Boolean {
                        if (node.receiver == null && overridden.name == node.methodName) {
                            context.report(
                                EMPTY_SUPER_CALL,
                                node,
                                context.getLocation(node),
                                "Do not call super implementation of method annotated with @EmptySuper"
                            )
                        }
                        return true
                    }
                })
            }
        }
    }

    private fun UMethodCallExpression.getMethodName(): String? = (this as? USimpleNameReferenceExpression)?.name

}