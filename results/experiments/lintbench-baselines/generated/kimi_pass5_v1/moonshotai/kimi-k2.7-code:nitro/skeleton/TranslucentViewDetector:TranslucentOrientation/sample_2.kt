package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import com.intellij.psi.*
import java.util.EnumSet
import org.jetbrains.uast.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class TranslucentViewDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private const val ANDROID_O = 26
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        private val IMPLEMENTATION = Implementation(
            TranslucentViewDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation while using a translucent theme is not
                supported on apps with `targetSdkVersion` O or greater. A visible activity behind
                your translucent activity may request a conflicting orientation; on Android O and
                later devices this causes an `IllegalStateException`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }

    private var targetSdkVersion = -1
    private var applicationTranslucent = false
    private val translucentJavaClasses = mutableSetOf<String>()
    private val pendingOrientationCalls = mutableListOf<Pair<JavaContext, UCallExpression>>()

    override fun getApplicableAttributes(): Collection<String>? =
        listOf("android:targetSdkVersion")

    override fun getApplicableElements(): Collection<String>? =
        listOf("activity", "application", "uses-sdk")

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.MANIFEST

    override fun beforeCheckFile(context: Context) {
        when (context) {
            is XmlContext -> {
                if (context.resourceFolderType == ResourceFolderType.MANIFEST) {
                    targetSdkVersion = -1
                    applicationTranslucent = false
                }
            }
            is JavaContext -> {
                translucentJavaClasses.clear()
                pendingOrientationCalls.clear()
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (attribute.name == "android:targetSdkVersion") {
            targetSdkVersion = attribute.value?.toIntOrNull() ?: -1
        }
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            "application" -> {
                val theme = element.getAttributeNS(ANDROID_URI, "theme")
                if (isTranslucentTheme(theme)) {
                    applicationTranslucent = true
                }
            }
            "activity" -> {
                val orientation = element.getAttributeNS(ANDROID_URI, "screenOrientation")
                if (orientation.isNullOrBlank()) return

                val theme = element.getAttributeNS(ANDROID_URI, "theme")
                when {
                    isTranslucentTheme(theme) ->
                        reportXmlIncident(context, element, orientation, theme)
                    applicationTranslucent && !looksLikeOpaqueTheme(theme) ->
                        reportXmlIncident(context, element, orientation, null)
                }
            }
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val targetSdk = if (targetSdkVersion != -1) {
            targetSdkVersion
        } else {
            context.project.buildTargetSdkVersion
        }
        return targetSdk >= ANDROID_O
    }

    override fun getApplicableMethodNames(): List<String>? =
        listOf("setTheme", "setRequestedOrientation")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val className = getActivityClassName(context, node) ?: return
        when (method.name) {
            "setTheme" -> {
                val arg = node.valueArguments.firstOrNull()
                if (arg != null && isTranslucentThemeReference(arg)) {
                    translucentJavaClasses.add(className)
                }
            }
            "setRequestedOrientation" -> {
                if (className.contains("Translucent", ignoreCase = true) ||
                    translucentJavaClasses.contains(className)
                ) {
                    reportJavaIncident(context, node, className)
                } else {
                    pendingOrientationCalls.add(context to node)
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (context !is JavaContext) return
        for ((ctx, node) in pendingOrientationCalls) {
            val className = getActivityClassName(ctx, node) ?: continue
            if (className.contains("Translucent", ignoreCase = true) ||
                translucentJavaClasses.contains(className)
            ) {
                reportJavaIncident(ctx, node, className)
            }
        }
        translucentJavaClasses.clear()
        pendingOrientationCalls.clear()
    }

    private fun reportXmlIncident(
        context: XmlContext,
        element: Element,
        orientation: String,
        theme: String?
    ) {
        val message = if (theme != null) {
            "Activity requests a fixed screen orientation ($orientation) while using a translucent theme ($theme)"
        } else {
            "Activity requests a fixed screen orientation ($orientation) while the application theme is translucent"
        }
        context.report(Incident(ISSUE, element, context.getLocation(element), message))
    }

    private fun reportJavaIncident(
        context: JavaContext,
        node: UCallExpression,
        className: String
    ) {
        val message =
            "Activity $className calls setRequestedOrientation() while using a translucent theme"
        context.report(Incident(ISSUE, node, context.getLocation(node), message))
    }

    private fun getActivityClassName(context: JavaContext, node: UCallExpression): String? {
        var current: UClass? = node.getParentOfType(UClass::class.java, true)
        while (current != null) {
            val psiClass = current.javaPsi ?: return null
            val qualifiedName = current.qualifiedName ?: return null
            if (context.evaluator.extendsClass(psiClass, "android.app.Activity", false) ||
                context.evaluator.extendsClass(psiClass, "android.support.v7.app.AppCompatActivity", false) ||
                context.evaluator.extendsClass(psiClass, "androidx.appcompat.app.AppCompatActivity", false)
            ) {
                return qualifiedName
            }
            current = current.getParentOfType(UClass::class.java, true)
        }
        return null
    }

    private fun isTranslucentThemeReference(expression: UExpression): Boolean {
        val text = expression.asSourceString()
        return text.contains("translucent", ignoreCase = true) ||
            text.contains("windowIsTranslucent", ignoreCase = true)
    }

    private fun isTranslucentTheme(theme: String?): Boolean {
        return theme != null && theme.contains("translucent", ignoreCase = true)
    }

    private fun looksLikeOpaqueTheme(theme: String?): Boolean {
        return !theme.isNullOrBlank() && !isTranslucentTheme(theme)
    }
}