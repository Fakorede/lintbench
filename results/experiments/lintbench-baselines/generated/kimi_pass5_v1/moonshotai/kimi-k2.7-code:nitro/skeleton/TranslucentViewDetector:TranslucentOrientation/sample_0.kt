package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.w3c.dom.Attr
import org.w3c.dom.Element

class TranslucentViewDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val ACTIVITY_TAG = "activity"
        private const val ATTR_SCREEN_ORIENTATION = "screenOrientation"
        private const val ATTR_THEME = "theme"

        private val NON_FIXED_ORIENTATIONS = setOf(
            "unspecified",
            "user",
            "behind",
            "sensor",
            "fullsensor",
            "nosensor"
        )

        private val IMPLEMENTATION = Implementation(
            TranslucentViewDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE, Scope.MANIFEST),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported
                on apps with targetSdkVersion O or greater, since there can be another activity
                visible behind your activity with a conflicting request. For example, your activity
                requests landscape and the visible activity behind your translucent activity
                requests portrait. In this case the system can only honor one of the requests and
                currently prefers to honor the request from non-translucent activities since there
                is nothing visible behind them. Devices running platform version O or greater will
                throw an exception in your app if this state is detected.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = listOf(
        ATTR_SCREEN_ORIENTATION,
        ATTR_THEME
    )

    override fun getApplicableElements(): Collection<String>? = null

    override fun appliesTo(folderType: ResourceFolderType): Boolean = false

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val owner = attribute.ownerElement ?: return
        if (owner.tagName != ACTIVITY_TAG) return

        when (attribute.localName) {
            ATTR_SCREEN_ORIENTATION -> {
                if (!isFixedOrientation(attribute.value)) return
                val theme = owner.getAttributeNS(ANDROID_URI, ATTR_THEME) ?: return
                if (isTranslucentTheme(theme)) {
                    reportManifestIncident(context, attribute)
                }
            }
            ATTR_THEME -> {
                if (!isTranslucentTheme(attribute.value)) return
                val orientation = owner.getAttributeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION) ?: return
                if (isFixedOrientation(orientation)) {
                    reportManifestIncident(context, attribute)
                }
            }
        }
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Manifest attributes are handled in visitAttribute.
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val targetSdk = context.project?.targetSdkVersion?.featureLevel
        return targetSdk == null || targetSdk >= 26 // Android O
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "setRequestedOrientation",
        "setTheme"
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val containingMethod = getContainingMethod(node) ?: return

        when (method.name) {
            "setRequestedOrientation" -> {
                val arg = node.valueArguments.getOrNull(0)?.asSourceString() ?: return
                if (!isFixedOrientation(arg)) return
                val translucentSetTheme = containingMethod.findCalls("setTheme").any {
                    isTranslucentTheme(it.valueArguments.getOrNull(0)?.asSourceString())
                }
                if (translucentSetTheme) {
                    context.report(
                        Incident(
                            ISSUE,
                            context.getLocation(node),
                            "Setting a fixed screen orientation is not supported when the activity uses a translucent theme (targetSdkVersion O or higher)."
                        )
                    )
                }
            }
            "setTheme" -> {
                val arg = node.valueArguments.getOrNull(0)?.asSourceString() ?: return
                if (!isTranslucentTheme(arg)) return
                val fixedOrientationCall = containingMethod.findCalls("setRequestedOrientation").any {
                    isFixedOrientation(it.valueArguments.getOrNull(0)?.asSourceString())
                }
                if (fixedOrientationCall) {
                    context.report(
                        Incident(
                            ISSUE,
                            context.getLocation(node),
                            "A translucent theme should not be combined with a fixed screen orientation (targetSdkVersion O or higher)."
                        )
                    )
                }
            }
        }
    }

    private fun reportManifestIncident(context: XmlContext, attribute: Attr) {
        val message = "This activity declares a fixed screenOrientation and a translucent theme, which is not supported when targetSdkVersion is O or higher."
        context.report(
            Incident(
                ISSUE,
                context.getLocation(attribute),
                message
            )
        )
    }

    private fun isFixedOrientation(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val normalized = value.substringAfterLast('.').lowercase()
        return normalized !in NON_FIXED_ORIENTATIONS
    }

    private fun isTranslucentTheme(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val name = value.substringAfterLast('/').lowercase()
        return name.contains("translucent") || name.contains("transparent")
    }

    private fun getContainingMethod(node: UCallExpression): UMethod? {
        var current: UElement? = node
        while (current != null && current !is UMethod) {
            current = current.uastParent
        }
        return current as? UMethod
    }

    private fun UElement.findCalls(name: String): List<UCallExpression> {
        val result = mutableListOf<UCallExpression>()
        for (child in children) {
            if (child is UCallExpression && child.methodName == name) {
                result.add(child)
            }
            result.addAll(child.findCalls(name))
        }
        return result
    }
}