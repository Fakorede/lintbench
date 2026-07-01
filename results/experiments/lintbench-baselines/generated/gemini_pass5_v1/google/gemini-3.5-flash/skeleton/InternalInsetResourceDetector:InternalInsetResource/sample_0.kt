package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

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
            explanation = "The internal inset dimension resources are not a supported way to retrieve the relevant insets for your application. The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI. To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("getIdentifier")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInSubclassOf(method, "android.content.res.Resources", false)) {
            return
        }

        val args = node.valueArguments
        if (args.size < 3) return

        val nameExpr = args[0]
        val defTypeExpr = args[1]
        val defPackageExpr = args[2]

        val nameString = nameExpr.evaluate() as? String ?: return
        val defTypeString = defTypeExpr.evaluate() as? String
        val defPackageString = defPackageExpr.evaluate() as? String

        var finalPkg = defPackageString
        var finalType = defTypeString
        var finalEntry = nameString

        val colonIndex = nameString.indexOf(':')
        if (colonIndex != -1) {
            finalPkg = nameString.substring(0, colonIndex)
            val rest = nameString.substring(colonIndex + 1)
            val slashIndex = rest.indexOf('/')
            if (slashIndex != -1) {
                finalType = rest.substring(0, slashIndex)
                finalEntry = rest.substring(slashIndex + 1)
            } else {
                finalEntry = rest
            }
        } else {
            val slashIndex = nameString.indexOf('/')
            if (slashIndex != -1) {
                finalType = nameString.substring(0, slashIndex)
                finalEntry = nameString.substring(slashIndex + 1)
            }
        }

        if (finalPkg == "android" && finalType == "dimen") {
            val badInsets = setOf(
                "status_bar_height",
                "navigation_bar_height",
                "navigation_bar_height_landscape",
                "navigation_bar_width",
                "status_bar_height_portrait"
            )
            if (badInsets.contains(finalEntry)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Using internal inset dimension resource `$finalEntry`"
                )
            }
        }
    }
}