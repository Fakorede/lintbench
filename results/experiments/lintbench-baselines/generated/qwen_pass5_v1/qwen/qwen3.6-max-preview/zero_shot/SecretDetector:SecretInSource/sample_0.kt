package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import java.util.EnumSet
import java.util.Locale

class SecretDetector : Detector(), Detector.UastScanner {

    companion object {
        private val SECRET_NAME_PATTERN = Regex("(?i).*(api[_\\-]?key|secret|password|token|auth|credential|private[_\\-]?key|access[_\\-]?key).*")

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
            severity = Severity.ERROR,
            implementation = Implementation(
                SecretDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE_SCOPE)
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UVariable::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitVariable(node: UVariable) {
                val name = node.name ?: return
                if (!SECRET_NAME_PATTERN.matches(name)) return

                val initializer = node.uastInitializer ?: return
                if (initializer !is ULiteralExpression) return

                val value = initializer.value as? String ?: return
                if (value.isBlank() || value.length < 8) return

                val upper = value.uppercase(Locale.ROOT)
                if (upper == "YOUR_API_KEY" || upper == "REPLACE_ME" || upper == "XXX" || upper == "TODO") return

                context.report(
                    ISSUE,
                    context.getLocation(initializer),
                    "Potential secret hardcoded in source code. Use the Secrets Gradle Plugin or build config fields instead."
                )
            }
        }
    }
}