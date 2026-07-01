package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.*
import org.jetbrains.uast.*

class InternalInsetResourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            InternalInsetResourceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to \
                retrieve the relevant insets for your application. The insets are \
                dynamic values that can change while your app is visible, and your \
                app's window may not intersect with the system UI. To get the \
                relevant value for your app and listen to updates, use \
                `androidx.core.view.WindowInsetsCompat` and related APIs.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getIdentifier")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, "android.content.res.Resources")) {
            return
        }

        val args = node.valueArguments
        if (args.isEmpty()) return

        val name = evaluateString(args.getOrNull(0)) ?: return
        val defType = args.getOrNull(1)?.let { evaluateString(it) }
        val defPackage = args.getOrNull(2)?.let { evaluateString(it) }

        var resPackage = defPackage
        var resType = defType
        var resName = name

        if (name.contains("/")) {
            val slashIndex = name.indexOf('/')
            val typePart = name.substring(0, slashIndex)
            resName = name.substring(slashIndex + 1)
            if (typePart.contains(":")) {
                val colonIndex = typePart.indexOf(':')
                resPackage = typePart.substring(0, colonIndex)
                resType = typePart.substring(colonIndex + 1)
            } else {
                resType = typePart
            }
        } else if (name.contains(":")) {
            val colonIndex = name.indexOf(':')
            resPackage = name.substring(0, colonIndex)
            resName = name.substring(colonIndex + 1)
        }

        val insetNames = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_width",
            "status_bar_height_landscape",
            "navigation_bar_height_landscape"
        )

        if (resName in insetNames && resPackage == "android" && (resType == null || resType == "dimen")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using internal inset dimension resource `$resName` is not supported"
            )
        }
    }

    private fun evaluateString(expression: UExpression?): String? {
        if (expression == null) return null
        val constant = expression.evaluate()
        if (constant is String) {
            return constant
        }
        return null
    }
}