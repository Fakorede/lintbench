package com.android.tools.lint.checks

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
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UVariable
import org.w3c.dom.Node
import java.util.regex.Pattern

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        private val GOOGLE_API_KEY_PATTERN = Pattern.compile("AIzaSy[A-Za-z0-9_\\-]*")

        @JvmField
        val ISSUE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = "Including secrets, such as API keys, in source code is a security risk. " +
                    "It is generally best practice to not include API keys in source code, " +
                    "and instead use something like the Secrets Gradle Plugin for Android.",
            category = Category.SECURITY,
            priority = 8,
            severity = Severity.WARNING,
            moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin",
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value as? String ?: return
                if (value.isBlank()) return

                // 1. Check if it matches Google API key pattern
                if (GOOGLE_API_KEY_PATTERN.matcher(value).find()) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Do not hardcode Google API keys in source code"
                    )
                    return
                }

                // 2. Check if it is associated with a suspect variable/name
                if (value.length >= 4) {
                    val suspectName = getAssociatedSuspectName(node)
                    if (suspectName != null && !isPlaceholder(value, suspectName)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Do not hardcode secrets like $suspectName in source code"
                        )
                    }
                }
            }
        }
    }

    private fun getAssociatedSuspectName(node: ULiteralExpression): String? {
        var current: UElement? = node
        while (current != null) {
            if (current is UVariable) {
                val name = current.name
                if (name != null && isSuspect(name)) {
                    return name
                }
            }
            if (current is UBinaryExpression) {
                val left = current.leftOperand.asSourceString().trim()
                if (isSuspect(left)) {
                    return left
                }
            }
            if (current is UCallExpression) {
                val methodName = current.methodName
                if (methodName != null && isSuspect(methodName)) {
                    return methodName
                }
                if (methodName == "to") {
                    val receiver = current.receiver
                    if (receiver is ULiteralExpression) {
                        val recVal = receiver.value as? String
                        if (recVal != null && isSuspect(recVal)) {
                            return recVal
                        }
                    }
                }
                for (arg in current.valueArguments) {
                    if (arg is ULiteralExpression) {
                        val argVal = arg.value as? String
                        if (argVal != null && isSuspect(argVal) && arg != node) {
                            return argVal
                        }
                    }
                }
            }
            current = current.uastParent
        }
        return null
    }

    private fun isSuspect(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("key") ||
               lower.contains("secret") ||
               lower.contains("password") ||
               lower.contains("passwd") ||
               lower.contains("token") ||
               lower.contains("credential") ||
               lower.contains("private") ||
               lower.contains("auth")
    }

    private fun isPlaceholder(value: String, suspectName: String?): Boolean {
        if (value.isBlank()) return true
        if (suspectName != null && value.equals(suspectName, ignoreCase = true)) return true
        val lower = value.lowercase()
        return lower == "null" || lower == "todo"
    }
}