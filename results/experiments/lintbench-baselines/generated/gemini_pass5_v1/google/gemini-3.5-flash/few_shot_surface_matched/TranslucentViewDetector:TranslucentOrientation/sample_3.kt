package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class TranslucentViewDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported \
                on apps with `targetSdkVersion` O or greater since there can be another activity \
                visible behind your activity with a conflicting request.
                
                For example, your activity requests landscape and the visible activity behind \
                your translucent activity request portrait. In this case the system can only \
                honor one of the requests and currently prefers to honor the request from \
                non-translucent activities since there is nothing visible behind them.
                
                Devices running platform version O or greater will throw an exception in your \
                app if this state is detected.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                java.util.EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE, Scope.JAVA_FILE)
            )
        )
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf("screenOrientation", "theme")
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("activity")
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return true
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        val name = attribute.localName ?: attribute.name
        if (name == "screenOrientation") {
            val element = attribute.ownerElement
            if (element.tagName == "activity") {
                val orientation = attribute.value
                if (isFixedOrientation(orientation)) {
                    val theme = element.getAttributeNS("http://schemas.android.com/apk/res/android", "theme")
                    if (theme.isNotEmpty() && isTranslucentThemeName(theme)) {
                        context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            "Should not specify screenOrientation with a translucent theme"
                        )
                    }
                }
            }
        }
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        if (element.tagName == "activity") {
            val orientationAttr = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "screenOrientation")
            val themeAttr = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "theme")
            if (orientationAttr != null && themeAttr != null) {
                val orientation = orientationAttr.value
                val theme = themeAttr.value
                if (isFixedOrientation(orientation) && isTranslucentThemeName(theme)) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Should not specify screenOrientation with a translucent theme"
                    )
                }
            }
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return true
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("setRequestedOrientation")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInSubClassOf(method, "android.app.Activity")) {
            val location = context.getNameLocation(node)
            context.report(
                ISSUE,
                node,
                location,
                "Setting orientation dynamically on a translucent activity can cause crashes on Android O and above"
            )
        }
    }

    private fun isFixedOrientation(orientation: String): Boolean {
        return orientation == "portrait" ||
               orientation == "landscape" ||
               orientation == "reversePortrait" ||
               orientation == "reverseLandscape" ||
               orientation == "sensorPortrait" ||
               orientation == "sensorLandscape" ||
               orientation == "userPortrait" ||
               orientation == "userLandscape"
    }

    private fun isTranslucentThemeName(theme: String): Boolean {
        return theme.contains("Translucent", ignoreCase = true) ||
               theme.contains("Floating", ignoreCase = true) ||
               theme.contains("Dialog", ignoreCase = true)
    }
}