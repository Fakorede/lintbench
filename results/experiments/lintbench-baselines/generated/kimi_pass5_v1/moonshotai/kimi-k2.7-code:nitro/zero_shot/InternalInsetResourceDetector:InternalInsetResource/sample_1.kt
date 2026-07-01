package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.w3c.dom.Attr

class InternalInsetResourceDetector : Detector(), SourceCodeScanner, ResourceXmlDetector {

    companion object {
        private val INSET_RESOURCES = setOf(
            "status_bar_height",
            "status_bar_height_landscape",
            "status_bar_height_portrait",
            "status_bar_height_default",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_height_portrait",
            "navigation_bar_width",
            "navigation_bar_width_landscape",
            "navigation_bar_width_portrait",
            "system_bars_vertical",
            "system_bars_horizontal",
            "waterfall_status_bar_inset",
            "waterfall_nav_bar_inset",
            "waterfall_side_inset",
            "waterfall_top_inset",
            "waterfall_bottom_inset"
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
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.JAVA_AND_RESOURCE_FILES
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UQualifiedReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                val selector = node.selector
                if (selector !is USimpleNameReferenceExpression) return
                val resourceName = selector.identifier
                if (resourceName !in INSET_RESOURCES) return

                val receiver = node.receiver as? UQualifiedReferenceExpression ?: return
                val dimenSelector = receiver.selector as? USimpleNameReferenceExpression ?: return
                if (dimenSelector.identifier != "dimen") return

                val rReceiver = receiver.receiver
                val isAndroidR = when (rReceiver) {
                    is UQualifiedReferenceExpression -> {
                        val rSelector = rReceiver.selector as? USimpleNameReferenceExpression ?: return
                        if (rSelector.identifier != "R") return
                        val androidSelector = rReceiver.receiver as? USimpleNameReferenceExpression ?: return
                        androidSelector.identifier == "android"
                    }
                    is USimpleNameReferenceExpression -> {
                        val resolved = rReceiver.resolve()
                        resolved is PsiClass && resolved.qualifiedName == "android.R"
                    }
                    else -> return
                }

                if (!isAndroidR) return

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    ISSUE.getBriefDescription(TextFormat.TEXT)
                )
            }
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return true
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return null
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        if (!value.startsWith("@android:dimen/") && !value.startsWith("@*android:dimen/")) return
        val name = value.substringAfter("dimen/")
        if (name !in INSET_RESOURCES) return

        context.report(
            ISSUE,
            attribute,
            context.getValueLocation(attribute),
            ISSUE.getBriefDescription(TextFormat.TEXT)
        )
    }
}