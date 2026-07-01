package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UVariable
import java.util.regex.Pattern

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        private val GOOGLE_API_KEY_PATTERN = Pattern.compile("AIzaSy[A-Za-z0-9\\-_]{33}")
        
        private val SUSPECT_WORDS = listOf(
            "API_KEY", 
            "SECRET", 
            "PRIVATE_KEY", 
            "PASSWORD", 
            "CREDENTIAL", 
            "AUTH_TOKEN"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk. \
                It is generally best practice to not include API keys in source code, \
                and instead use something like the Secrets Gradle Plugin for Android.
                """.trimIndent(),
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
        return listOf(UVariable::class.java, ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value as? String ?: return
                if (GOOGLE_API_KEY_PATTERN.matcher(value).find()) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Do not hardcode Google API keys in source code"
                    )
                }
            }

            override fun visitVariable(node: UVariable) {
                val name = node.name ?: return
                val upperName = name.uppercase()
                if (SUSPECT_WORDS.any { upperName.contains(it) }) {
                    val initializer = node.uastInitializer ?: return
                    if (initializer is ULiteralExpression) {
                        val value = initializer.value as? String ?: return
                        if (value.isNotBlank() && value.length > 4) {
                            if (!GOOGLE_API_KEY_PATTERN.matcher(value).find()) {
                                context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(initializer),
                                    "Do not hardcode secrets like $name in source code"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}