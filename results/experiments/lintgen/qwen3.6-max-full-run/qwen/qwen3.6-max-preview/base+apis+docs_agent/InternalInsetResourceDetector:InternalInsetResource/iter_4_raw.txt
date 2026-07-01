package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.EnumSet

class InternalInsetResourceDetector : Detector(), XmlScanner, SourceCodeScanner {
    companion object {
        private val INTERNAL_INSET_DIMENS = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "system_bar_height",
            "status_bar_height_reduced",
            "navigation_bar_height_reduced",
            "quick_qs_offset_height",
            "quick_qs_total_height",
            "navigation_bar_frame_height",
            "navigation_bar_gesture_height"
        )

        val ISSUE: Issue = Issue.create(
            "InternalInsetResource",
            "Using internal inset dimension resource",
            "The internal inset dimension resources are not a supported way to " +
                "retrieve the relevant insets for your application. The insets are " +
                "dynamic values that can change while your app is visible, and your " +
                "app's window may not intersect with the system UI. " +
                "To get the relevant value for your app and listen to updates, use " +
                "`androidx.core.view.WindowInsetsCompat` and related APIs.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(
                InternalInsetResourceDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE)
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            checkXmlValue(context, attr, attr.value)
        }
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node.nodeType == Node.TEXT_NODE) {
                checkXmlValue(context, element, node.nodeValue)
            }
        }
    }

    private fun checkXmlValue(context: XmlContext, node: Node, value: String?) {
        if (value.isNullOrEmpty()) return
        val regex = Regex("@android:dimen/(\\w+)")
        for (match in regex.findAll(value)) {
            val dimenName = match.groupValues[1]
            if (INTERNAL_INSET_DIMENS.contains(dimenName)) {
                context.report(
                    ISSUE,
                    context.getLocation(node),
                    "Using internal inset dimension resource `@android:dimen/$dimenName`. " +
                        "Use `WindowInsetsCompat` instead."
                )
            }
        }
    }

    override fun appliesToResourceRefs(): Boolean = true

    override fun visitResourceReference(
        context: JavaContext,
        node: UElement,
        type: ResourceType,
        name: String,
        isFramework: Boolean
    ) {
        if (isFramework && type == ResourceType.DIMEN && INTERNAL_INSET_DIMENS.contains(name)) {
            context.report(
                ISSUE,
                context.getLocation(node),
                "Using internal inset dimension resource `android.R.dimen.$name`. " +
                    "Use `WindowInsetsCompat` instead."
            )
        }
    }
}