package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Detector.SourceCodeScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastUtils

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ChromeOsSourceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Poor performance with APIs inside `onConfigurationChanged()`",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an
                `onConfigurationChanged()` call occurs. If this method contains calls that
                invalidate or request layout on views—such as `invalidate()`,
                `requestLayout()`, `forceLayout()`, `setText()`, `setBackground*()`,
                `setImage*()`, and various adapter `notify*()` methods—the UI is forced to
                redraw. On large screens this can cause jank and poor performance during
                resizing.

                Move UI update/redraw logic out of `onConfigurationChanged()` or defer it so
                that it does not run synchronously while the configuration change is being
                processed.
            """.trimIndent(),
            category = Category.CHROME_OS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    private val redrawMethods = setOf(
        "invalidate",
        "requestLayout",
        "forceLayout",
        "refreshDrawableState",
        "setText",
        "setHint",
        "setBackground",
        "setBackgroundColor",
        "setBackgroundResource",
        "setBackgroundDrawable",
        "setImageDrawable",
        "setImageResource",
        "setImageBitmap",
        "setImageURI",
        "setVisibility",
        "setEnabled",
        "setAlpha",
        "setColorFilter",
        "notifyDataSetChanged",
        "notifyItemChanged",
        "notifyItemInserted",
        "notifyItemRemoved",
        "notifyItemRangeChanged",
        "notifyItemRangeInserted",
        "notifyItemRangeRemoved"
    )

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = null

    override fun getApplicableMethodNames(): List<String>? = redrawMethods.toList()

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (!isInsideOnConfigurationChanged(node)) return

        val name = node.methodName ?: return
        if (name !in redrawMethods) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Avoid calling `$name` inside `onConfigurationChanged()` because it can trigger a UI redraw and degrade performance when resizing on large screens."
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {}
            override fun visitCallExpression(node: UCallExpression) {}
        }

    private fun isInsideOnConfigurationChanged(node: UCallExpression): Boolean {
        val containingMethod = UastUtils.getParentOfType(
            node, UMethod::class.java, true
        ) ?: return false

        if (containingMethod.name != "onConfigurationChanged") return false

        val params = containingMethod.uastParameters
        return params.size == 1 &&
                params[0].psi.type.canonicalText == "android.content.res.Configuration"
    }
}