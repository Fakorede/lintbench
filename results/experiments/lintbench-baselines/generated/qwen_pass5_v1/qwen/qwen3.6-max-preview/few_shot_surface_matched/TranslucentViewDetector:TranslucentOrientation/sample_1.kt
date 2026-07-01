package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceFile
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
        private val FIXED_ORIENTATIONS = setOf(
            "landscape", "portrait", "reverseLandscape", "reversePortrait",
            "sensorLandscape", "sensorPortrait", "userLandscape", "userPortrait"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported \
                on apps with targetSdkVersion O or greater since there can be another activity \
                visible behind your activity with a conflicting request. Devices running \
                platform version O or greater will throw an exception in your app if this \
                state is detected.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("activity")
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return listOf("screenOrientation")
    }

    override fun appliesTo(context: Context, file: ResourceFile): Boolean {
        return true
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val themeAttr = element.getAttributeNodeNS(ANDROID_URI, "theme") ?: return
        if (!isTranslucentTheme(themeAttr.value)) return

        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, "screenOrientation") ?: return
        val orientationValue = orientationAttr.value.substringAfter("/")
        if (FIXED_ORIENTATIONS.contains(orientationValue)) {
            context.report(
                Incident(
                    ISSUE,
                    orientationAttr,
                    context.getValueLocation(orientationAttr),
                    "Fixed screen orientation is not supported with translucent themes on Android O+"
                )
            )
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val element = attribute.ownerElement as? Element ?: return
        val themeAttr = element.getAttributeNodeNS(ANDROID_URI, "theme") ?: return
        if (!isTranslucentTheme(themeAttr.value)) return

        val orientationValue = attribute.value.substringAfter("/")
        if (FIXED_ORIENTATIONS.contains(orientationValue)) {
            context.report(
                Incident(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Fixed screen orientation is not supported with translucent themes on Android O+"
                )
            )
        }
    }

    private fun isTranslucentTheme(themeValue: String): Boolean {
        return themeValue.contains("Translucent", ignoreCase = true) ||
               themeValue.contains("Dialog", ignoreCase = true) ||
               themeValue.contains("Floating", ignoreCase = true)
    }

    override fun filterIncident(context: Context, incident: Incident): Boolean {
        return context.targetSdk >= 26
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("setRequestedOrientation")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInSubClassOf(method, "android.app.Activity", false)) {
            return
        }
        val location = context.getLocation(node)
        context.report(
            Incident(
                ISSUE,
                node,
                location,
                "Calling setRequestedOrientation with a fixed orientation may crash on Android O+ if the activity uses a translucent theme"
            )
        )
    }
}