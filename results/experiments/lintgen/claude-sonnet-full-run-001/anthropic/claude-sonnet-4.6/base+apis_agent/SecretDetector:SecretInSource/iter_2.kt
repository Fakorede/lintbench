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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node
import java.util.EnumSet

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            SecretDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk. \
                It is generally best practice to not include API keys in source code, \
                and instead use something like the Secrets Gradle Plugin for Android.
            """,
            moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin",
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        private val SECRET_NAME_PATTERNS = listOf(
            Regex("(?i)api.?key"),
            Regex("(?i)secret.?key"),
            Regex("(?i)private.?key"),
            Regex("(?i)auth.?token"),
            Regex("(?i)access.?token"),
            Regex("(?i)client.?secret"),
            Regex("(?i)api.?secret"),
            Regex("(?i)app.?secret"),
            Regex("(?i)encryption.?key"),
            Regex("(?i)signing.?key"),
            Regex("(?i)oauth.?token"),
            Regex("(?i)bearer.?token"),
            Regex("(?i)maps.?key"),
            Regex("(?i)google.?key"),
            Regex("(?i)firebase.?key"),
            Regex("(?i)aws.?secret"),
            Regex("(?i)aws.?key"),
            Regex("(?i)stripe.?key"),
            Regex("(?i)twilio.?token"),
            Regex("(?i)sendgrid.?key"),
            Regex("(?i)github.?token"),
            Regex("(?i)slack.?token"),
            Regex("(?i)webhook.?secret"),
            Regex("(?i)\\bpassword\\b"),
            Regex("(?i)\\bpasswd\\b"),
            Regex("(?i)\\bsecret\\b"),
            Regex("(?i)\\btoken\\b"),
            Regex("(?i)\\bkey\\b")
        )

        // Known secret value patterns (high confidence)
        private val HIGH_CONFIDENCE_SECRET_PATTERNS = listOf(
            // Google API key
            Regex("AIza[0-9A-Za-z\\-_]{35}"),
            // AWS access key
            Regex("AKIA[0-9A-Z]{16}"),
            // JWT token
            Regex("ey[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+"),
            // Long hex strings (32+ chars)
            Regex("[0-9a-fA-F]{32,}"),
            // Base64-like strings (40+ chars)
            Regex("[A-Za-z0-9+/]{40,}={0,2}")
        )

        private val PLACEHOLDER_PATTERNS = listOf(
            Regex("(?i)your[_\\-. ]?(api[_\\-. ]?)?key"),
            Regex("(?i)your[_\\-. ]?secret"),
            Regex("(?i)your[_\\-. ]?token"),
            Regex("(?i)insert[_\\-. ]?key"),
            Regex("(?i)replace[_\\-. ]?me"),
            Regex("(?i)\\btodo\\b"),
            Regex("(?i)\\bfixme\\b"),
            Regex("(?i)placeholder"),
            Regex("(?i)\\bexample\\b"),
            Regex("(?i)\\bsample\\b"),
            Regex("(?i)\\bdummy\\b"),
            Regex("(?i)\\bfake\\b"),
            Regex("(?i)xxx+"),
            Regex("\\*{3,}"),
            Regex("(?i)<[^>]+>"),
            Regex("\\$\\{[^}]+\\}"),
            Regex("\\$[A-Z_][A-Z0-9_]+")
        )

        private const val MIN_SECRET_LENGTH = 8

        private fun isLikelyPlaceholder(value: String): Boolean {
            if (value.length < MIN_SECRET_LENGTH) return true
            for (pattern in PLACEHOLDER_PATTERNS) {
                if (pattern.containsMatchIn(value)) return true
            }
            // All same character repeated
            if (value.length > 3 && value.all { it == value[0] }) return true
            return false
        }

        private fun isHighConfidenceSecret(value: String): Boolean {
            if (value.isBlank() || value.length < MIN_SECRET_LENGTH) return false
            if (isLikelyPlaceholder(value)) return false
            for (pattern in HIGH_CONFIDENCE_SECRET_PATTERNS) {
                if (pattern.containsMatchIn(value)) return true
            }
            return false
        }

        private fun isSecretName(name: String): Boolean {
            for (pattern in SECRET_NAME_PATTERNS) {
                if (pattern.containsMatchIn(name)) return true
            }
            return false
        }

        private fun isLikelySecretValueForName(value: String): Boolean {
            if (value.isBlank() || value.length < MIN_SECRET_LENGTH) return false
            if (isLikelyPlaceholder(value)) return false
            // If the name suggests a secret and the value is non-trivial, flag it
            return true
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(
            UField::class.java,
            ULocalVariable::class.java
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {

            override fun visitField(node: UField) {
                checkVariableDeclaration(context, node, node.name, node.uastInitializer)
            }

            override fun visitLocalVariable(node: ULocalVariable) {
                checkVariableDeclaration(context, node, node.name, node.uastInitializer)
            }

            private fun checkVariableDeclaration(
                context: JavaContext,
                node: UElement,
                name: String,
                initializer: UElement?
            ) {
                if (initializer == null) return

                val value = when (initializer) {
                    is ULiteralExpression -> initializer.value as? String
                    else -> null
                } ?: return

                val nameIsSecret = isSecretName(name)
                val valueIsHighConfidenceSecret = isHighConfidenceSecret(value)

                val shouldReport = when {
                    // High confidence secret value pattern regardless of name
                    valueIsHighConfidenceSecret -> true
                    // Secret-sounding name with a plausible non-placeholder value
                    nameIsSecret && isLikelySecretValueForName(value) -> true
                    else -> false
                }

                if (shouldReport) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(initializer),
                        "Possible secret value assigned to `$name`. " +
                                "Avoid including secrets in source code; consider using the Secrets Gradle Plugin for Android."
                    )
                }
            }
        }
    }

    override fun getApplicableMethodNames(): List<String>? = null
    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {}
    override fun getApplicableConstructorTypes(): List<String>? = null
    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {}
    override fun getApplicableReferenceNames(): List<String>? = null
    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {}
    override fun applicableSuperClasses(): List<String>? = null
    override fun visitClass(context: JavaContext, declaration: UClass) {}
    override fun visitClass(context: JavaContext, lambda: ULambdaExpression) {}
    override fun appliesToResourceRefs(): Boolean = false
    override fun visitResourceReference(context: JavaContext, node: UElement, type: ResourceType, name: String, isFramework: Boolean) {}
    override fun applicableAnnotations(): List<String>? = null
    override fun visitAnnotationUsage(context: JavaContext, element: UElement, annotationInfo: AnnotationInfo, usageInfo: AnnotationUsageInfo) {}
    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean = false
    override fun inheritAnnotation(annotation: String): Boolean = false
    override fun visitAnnotationUsage(context: XmlContext, reference: Node, annotationInfo: AnnotationInfo, usageInfo: AnnotationUsageInfo) {}
    override fun isCallGraphRequired(): Boolean = false
    override fun analyzeCallGraph(context: Context, callGraph: CallGraphResult) {}
}