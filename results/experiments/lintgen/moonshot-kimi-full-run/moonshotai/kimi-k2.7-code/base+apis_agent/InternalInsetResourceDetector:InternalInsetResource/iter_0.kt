package com.android.tools.lint.checks

import com.android.ide.common.resources.ResourceUrl
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
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
import org.jetbrains.uast.UElement
import org.w3c.dom.Attr

class InternalInsetResourceDetector : Detector(), XmlScanner, SourceCodeScanner {

    override fun getApplicableAttributes(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        val resourceUrl = ResourceUrl.parse(value) ?: return
        if (resourceUrl.type == ResourceType.DIMEN &&
            resourceUrl.isFramework &&
            resourceUrl.name in INSET_DIMENS
        ) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "Using internal inset dimension resource `@dimen/${resourceUrl.name}`; use WindowInsetsCompat instead."
            )
        }
    }

    override fun visitResourceReference(
        context: JavaContext,
        node: UElement,
        type: ResourceType,
        name: String,
        isFramework: Boolean
    ) {
        if (type == ResourceType.DIMEN && isFramework && name in INSET_DIMENS) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using internal inset dimension resource `R.dimen.$name`; use WindowInsetsCompat instead."
            )
        }
    }

    companion object {
        private val INSET_DIMENS = setOf(
            "status_bar_height",
            "status_bar_height_default",
            "status_bar_height_landscape",
            "status_bar_height_portrait",
            "navigation_bar_height",
            "navigation_bar_height_default",
            "navigation_bar_height_landscape",
            "navigation_bar_height_portrait",
            "navigation_bar_width",
            "navigation_bar_width_default",
            "navigation_bar_width_landscape",
            "navigation_bar_width_portrait"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to retrieve the relevant insets for your application. The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI. To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}