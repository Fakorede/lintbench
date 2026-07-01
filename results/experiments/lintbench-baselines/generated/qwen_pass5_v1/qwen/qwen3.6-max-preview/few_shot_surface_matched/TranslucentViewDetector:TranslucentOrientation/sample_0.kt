package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
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
import org.w3c.dom.Element
import java.util.EnumSet

class TranslucentViewDetector : Detector(), XmlScanner, SourceCodeScanner {

    companion object {
        private const val API_O = 26
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = "Specifying a fixed screen orientation with a translucent theme isn't supported " +
                "on apps with targetSdkVersion O or greater since there can be another activity visible " +
                "behind your activity with a conflicting request. Devices running platform version O or " +
                "greater will throw an exception in your app if this state is detected.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("activity")

    override fun getApplicableAttributes(): Collection<String>? = listOf("screenOrientation")

    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.MANIFEST

    override fun visitElement(context: XmlContext, element: Element) {
        // Element-level checks are delegated to visitAttribute for precise location reporting
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr, value: String) {
        val fixedOrientations = setOf(
            "portrait", "landscape", "reversePortrait", "reverseLandscape",
            "sensorPortrait", "sensorLandscape", "userPortrait", "userLandscape"
        )
        if (value !in fixedOrientations) return

        val activity = attribute.ownerElement
        var theme = activity.getAttributeNS(ANDROID_URI, "theme")
        if (theme.isEmpty()) {
            val parent = activity.parentNode
            if (parent is Element && parent.nodeName == "application") {
                theme = parent.getAttributeNS(ANDROID_URI, "theme")
            }
        }

        if (theme.contains("Translucent", ignoreCase = true)) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "Specifying a fixed screen orientation with a translucent theme is not supported on API 26+"
            )
        }
    }

    override fun filterIncident(context: Context, incident: Incident): Boolean {
        return context.targetSdk >= API_O
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("setRequestedOrientation")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInSubClassOf(method, "android.app.Activity")) return

        val message = "Calling setRequestedOrientation() in an Activity with a translucent theme " +
            "is not supported on API 26+ and will throw an exception."
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            message
        )
    }
}