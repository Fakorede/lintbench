package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UElementHandler
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiField
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Attr

class InternalInsetResourceDetector : Detector(), Detector.XmlScanner, Detector.UastScanner {

    companion object {
        private const val ISSUE_ID = "InternalInsetResource"

        private val INSET_DIMEN_NAMES = setOf(
            "status_bar_height",
            "status_bar_height_portrait",
            "status_bar_height_landscape",
            "status_bar_height_large",
            "navigation_bar_height",
            "navigation_bar_height_portrait",
            "navigation_bar_height_landscape",
            "navigation_bar_width",
            "navigation_bar_width_portrait",
            "navigation_bar_width_landscape",
            "system_bar_height"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = ISSUE_ID,
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to \
                retrieve the relevant insets for your application. The insets are \
                dynamic values that can change while your app is visible, and your \
                app's window may not intersect with the system UI. To get the \
                relevant value for your app and listen to updates, use \
                `androidx.core.view.WindowInsetsCompat` and related APIs.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun getApplicableAttributes(): Collection<String>? = ALL_ATTRIBUTES

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        val url = ResourceUrl.parse(value) ?: return
        if (url.type == ResourceType.DIMEN && url.name in INSET_DIMEN_NAMES) {
            reportUsage(context, attribute, url.name)
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitReferenceExpression(node: UReferenceExpression) {
                val field = node.resolve() as? PsiField ?: return
                val name = field.name
                if (name !in INSET_DIMEN_NAMES) return
                if (field.containingClass?.name == "dimen") {
                    reportUsage(context, node, name)
                }
            }
        }
    }

    private fun reportUsage(context: XmlContext, node: Attr, name: String) {
        context.report(
            ISSUE,
            node,
            context.getValueLocation(node),
            "Using internal inset dimension resource `$name`; use WindowInsetsCompat instead."
        )
    }

    private fun reportUsage(context: JavaContext, node: UReferenceExpression, name: String) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Using internal inset dimension resource `$name`; use WindowInsetsCompat instead."
        )
    }
}