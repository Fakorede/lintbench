package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class FileEndsWithDetector : Detector(), SourceCodeScanner {
    companion object {
        @JvmField
        val ISSUE = Issue.create(
            "FileEndsWithExt",
            "File endsWith on file extensions",
            "The `File.endsWith(suffix)` method checks whole path components, not just string suffixes. " +
            "This means that `File(\"foo.txt\").endsWith(\".txt\")` will return false. " +
            "Instead you might have intended `file.path.endsWith` or `file.extension.equals`.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            Implementation(FileEndsWithDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val methodName = node.methodName ?: return
                if (methodName != "endsWith") return

                val method = node.resolve() ?: return
                val qualifiedName = method.containingClass?.qualifiedName ?: return
                if (qualifiedName != "java.io.File") return

                val args = node.valueArguments
                if (args.size != 1) return
                val arg = args[0]

                if (arg is ULiteralExpression && arg.value is String) {
                    val suffix = arg.value as String
                    if (suffix.startsWith(".")) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Using `File.endsWith(\"$suffix\")` checks path components, not string suffixes. " +
                            "Use `file.path.endsWith(\"$suffix\")` or check `file.extension` instead."
                        )
                    }
                }
            }
        }
    }
}