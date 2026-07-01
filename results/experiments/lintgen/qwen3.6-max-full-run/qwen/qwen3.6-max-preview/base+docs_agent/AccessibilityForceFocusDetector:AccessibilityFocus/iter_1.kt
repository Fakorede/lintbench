package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr

class AccessibilityForceFocusDetector : Detector(), XmlScanner {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val ATTR_IMPORTANT_FOR_ACCESSIBILITY = "importantForAccessibility"

        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.",
            category = Category.ACCESSIBILITY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableAttributes(): Collection<String>? =
        listOf(ATTR_IMPORTANT_FOR_ACCESSIBILITY)

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (attribute.namespaceURI != ANDROID_URI) return
        if (attribute.value == "yes") {
            context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "Forcing accessibility focus via `android:importantForAccessibility=\"yes\"` interferes with screen readers. Remove this attribute or use `auto` to allow the system to manage focus naturally."
            )
        }
    }
}