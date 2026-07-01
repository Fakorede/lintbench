package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Attr
import java.util.EnumSet

class InternalInsetResourceDetector : Detector(), SourceCodeScanner, XmlScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("getIdentifier")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "android.content.res.Resources")) return

        val args = node.valueArguments
        if (args.size < 3) return

        val nameArg = ConstantEvaluator.evaluate(context, args[0]) as? String ?: return
        val defTypeArg = ConstantEvaluator.evaluate(context, args[1]) as? String
        val defPackageArg = ConstantEvaluator.evaluate(context, args[2]) as? String

        val (resolvedName, resolvedType, resolvedPackage) = resolveIdentifierArgs(nameArg, defTypeArg, defPackageArg)

        if (resolvedPackage == "android" && resolvedType == "dimen" && FORBIDDEN_RESOURCES.contains(resolvedName)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using internal inset dimension resource `$resolvedName` is not supported"
            )
        }
    }

    private fun resolveIdentifierArgs(name: String, defType: String?, defPackage: String?): Triple<String, String?, String?> {
        var resolvedName = name
        var resolvedType = defType
        var resolvedPackage = defPackage

        if (resolvedName.contains(":")) {
            resolvedPackage = resolvedName.substringBefore(":")
            resolvedName = resolvedName.substringAfter(":")
        }
        if (resolvedName.contains("/")) {
            resolvedType = resolvedName.substringBefore("/")
            resolvedName = resolvedName.substringAfter("/")
        }

        return Triple(resolvedName, resolvedType, resolvedPackage)
    }

    override fun getApplicableAttributes(): Collection<String> = XmlScanner.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.nodeValue ?: return
        if (value.startsWith("@android:dimen/") || value.startsWith("@*android:dimen/")) {
            val name = value.substringAfterLast("/")
            if (FORBIDDEN_RESOURCES.contains(name)) {
                context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Using internal inset dimension resource `$name` is not supported"
                )
            }
        }
    }

    companion object {
        private val FORBIDDEN_RESOURCES = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width",
            "status_bar_height_portrait"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to retrieve the relevant insets for your application. \
                The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI. \
                To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE)
            )
        )
    }
}