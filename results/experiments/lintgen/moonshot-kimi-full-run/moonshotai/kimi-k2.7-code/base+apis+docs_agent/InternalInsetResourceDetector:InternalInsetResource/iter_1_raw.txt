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
import com.android.tools.lint.detector.api.XmlScannerConstants
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

class InternalInsetResourceDetector : Detector(), XmlScanner, SourceCodeScanner {

    companion object {
        private val INSET_DIMENS = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_width"
        )

        @JvmField
        val ISSUE = Issue.create(
            "InternalInsetResource",
            "Using internal inset dimension resource",
            """
                The internal inset dimension resources are not a supported way to retrieve the \
                relevant insets for your application. The insets are dynamic values that can \
                change while your app is visible, and your app's window may not intersect with \
                the system UI. To get the relevant value for your app and listen to updates, use \
                `androidx.core.view.WindowInsetsCompat` and related APIs.
            """.trimIndent(),
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private fun getReferencedDimenName(value: String): String? {
            val v = value.trim()
            val prefix = when {
                v.startsWith("@android:dimen/") -> "@android:dimen/"
                v.startsWith("@*android:dimen/") -> "@*android:dimen/"
                v.startsWith("?android:dimen/") -> "?android:dimen/"
                v.startsWith("?*android:dimen/") -> "?*android:dimen/"
                else -> return null
            }
            return v.substring(prefix.length).trim()
        }
    }

    override fun getApplicableAttributes(): Collection<String>? = XmlScannerConstants.ALL

    override fun getApplicableElements(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        val name = getReferencedDimenName(value) ?: return
        if (name !in INSET_DIMENS) return

        context.report(
            ISSUE,
            attribute,
            context.getValueLocation(attribute),
            "Using internal inset dimension resource `$name`"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        for (i in 0 until element.childNodes.length) {
            val child = element.childNodes.item(i)
            if (child.nodeType != Node.TEXT_NODE) continue
            val value = child.nodeValue ?: continue
            val name = getReferencedDimenName(value) ?: continue
            if (name !in INSET_DIMENS) continue

            context.report(
                ISSUE,
                child,
                context.getLocation(child),
                "Using internal inset dimension resource `$name`"
            )
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
        if (!isFramework || type != ResourceType.DIMEN) return
        if (name !in INSET_DIMENS) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Using internal inset dimension resource `$name`"
        )
    }
}