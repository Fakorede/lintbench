package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File endsWith on file extensions",
            explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components, \
                not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will \
                return false. Instead you might have intended `file.path.endsWith` or \
                `file.extension.equals`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                FileEndsWithDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Check that this is the File.endsWith(File) or File.endsWith(String) Kotlin extension
        val receiverType = node.receiverType ?: return
        val receiverTypeName = receiverType.canonicalText
        if (receiverTypeName != "java.io.File") return

        // Get the argument - we're looking for a string argument that starts with "."
        val arguments = node.valueArguments
        if (arguments.size != 1) return

        val argument = arguments[0]
        val argumentType = argument.getExpressionType()?.canonicalText

        // The Kotlin File.endsWith(String) extension is what we want to flag
        // Also flag File.endsWith(File) if the file path starts with "."
        if (argumentType == "java.lang.String" || argumentType == "String") {
            // Check if the argument looks like a file extension (starts with ".")
            val value = argument.evaluate()
            if (value is String && value.startsWith(".")) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "File.endsWith checks whole path components, not string suffixes; " +
                        "`File(\"foo.txt\").endsWith(\".txt\")` will return false. " +
                        "Did you mean `file.path.endsWith(\"${value}\")` or " +
                        "`file.extension.equals(\"${value.removePrefix(".")}\")`?"
                )
            } else if (value == null) {
                // Can't evaluate statically, but still flag it as suspicious
                // Only flag if we can determine it's the Kotlin extension method
                // by checking the declaring class
                val containingClass = method.containingClass?.qualifiedName
                // Kotlin's File.endsWith(String) is an extension in kotlin.io
                // The method will be on FilesKt or similar
                if (containingClass != null && (
                            containingClass.contains("FilesKt") ||
                            containingClass.contains("kotlin.io") ||
                            containingClass == "java.io.File"
                        )
                ) {
                    // Only report if the declaring class is java.io.File (the Kotlin extension
                    // adds endsWith(String) to File)
                    if (containingClass == "java.io.File" ||
                        containingClass.startsWith("kotlin")
                    ) {
                        // Don't report without a "." prefix - too many false positives
                    }
                }
            }
        } else if (argumentType == "java.io.File") {
            // File.endsWith(File) - check if the file argument is constructed with a "." prefix
            val value = argument.evaluate()
            if (value is String && value.startsWith(".")) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "File.endsWith checks whole path components, not string suffixes; " +
                        "`File(\"foo.txt\").endsWith(\".txt\")` will return false. " +
                        "Did you mean `file.path.endsWith(\"${value}\")`?"
                )
            } else if (argument is UCallExpression) {
                // Check if it's File(".txt") or similar
                val argArguments = argument.valueArguments
                if (argArguments.size == 1) {
                    val argValue = argArguments[0].evaluate()
                    if (argValue is String && argValue.startsWith(".")) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "File.endsWith checks whole path components, not string suffixes; " +
                                "`File(\"foo.txt\").endsWith(\".txt\")` will return false. " +
                                "Did you mean `file.path.endsWith(\"${argValue}\")` or " +
                                "`file.extension.equals(\"${argValue.removePrefix(".")}\")`?"
                        )
                    }
                }
            }
        }
    }
}