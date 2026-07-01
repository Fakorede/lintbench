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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.getAsJavaString
import org.jetbrains.uast.util.isMethodCall
import java.util.EnumSet

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    override fun getApplicableCallNames(): List<String> = listOf("endsWith")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, call: com.intellij.psi.PsiMethod) {
        if (!node.isMethodCall()) return

        val receiverType = node.receiverType ?: return
        if (receiverType.canonicalText != "java.io.File") return

        val args = node.valueArguments
        if (args.size != 1) return

        val arg = args[0] as? ULiteralExpression ?: return
        val suffix = arg.getAsJavaString() ?: return
        if (!suffix.startsWith(".")) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "`File.endsWith(\"$suffix\")` checks whole path components, not string suffixes; " +
                "use `file.path.endsWith(\"$suffix\")` or `file.extension == \"${suffix.substring(1)}\"` instead"
        )
    }

    companion object {
        private val IMPLEMENTATION = Implementation(
            FileEndsWithDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
        )

        val ISSUE = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "Suspicious `File.endsWith` extension call",
            explanation = """
                The Kotlin `java.io.File.endsWith(suffix)` extension checks whole path
                components, not raw string suffixes. For example,
                `File("foo.txt").endsWith(".txt")` returns false. If you are checking a
                file extension, use `file.path.endsWith(suffix)` or
                `file.extension == extension`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }
}