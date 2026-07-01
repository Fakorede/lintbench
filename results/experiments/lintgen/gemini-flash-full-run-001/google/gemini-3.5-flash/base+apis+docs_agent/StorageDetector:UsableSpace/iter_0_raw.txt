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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.visitor.AbstractUastVisitor

class StorageDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UFile::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitFile(node: UFile) {
                val visitor = StorageVisitor(context)
                node.accept(visitor)
                visitor.reportOps()
            }
        }
    }

    private class StorageVisitor(private val context: JavaContext) : AbstractUastVisitor() {
        val usableSpaceCalls = mutableListOf<UCallExpression>()
        var hasNewApi = false

        override fun visitCallExpression(node: UCallExpression): Boolean {
            val methodName = node.methodName
            if (methodName == "getUsableSpace") {
                val method = node.resolve()
                if (method != null) {
                    if (context.evaluator.isMemberInClass(method, "java.io.File")) {
                        usableSpaceCalls.add(node)
                    }
                } else {
                    usableSpaceCalls.add(node)
                }
            } else if (methodName == "getAllocatableBytes" || methodName == "allocateBytes") {
                val method = node.resolve()
                if (method != null) {
                    if (context.evaluator.isMemberInClass(method, "android.os.storage.StorageManager")) {
                        hasNewApi = true
                    }
                } else {
                    hasNewApi = true
                }
            }
            return super.visitCallExpression(node)
        }

        fun reportOps() {
            if (!hasNewApi) {
                for (call in usableSpaceCalls) {
                    context.report(
                        ISSUE,
                        call,
                        context.getLocation(call),
                        "Consider using `StorageManager.getAllocatableBytes` and `allocateBytes` instead of `getUsableSpace()`"
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UsableSpace",
            briefDescription = "Using getUsableSpace()",
            explanation = """
                When you need to allocate disk space for large files, consider using the new \
                `allocateBytes(FileDescriptor, long)` API, which will automatically clear \
                cached files belonging to other apps (as needed) to meet your request.

                When deciding if the device has enough disk space to hold your new data, \
                call `getAllocatableBytes(UUID)` instead of using `getUsableSpace()`, since \
                the former will consider any cached data that the system is willing to \
                clear on your behalf.

                Note that these methods require API level 26. If your app is running on \
                older devices, you will probably need to use both APIs, conditionally switching \
                on `Build.VERSION.SDK_INT`. Lint only looks in the same compilation unit to \
                see if you are already using both APIs, so if it warns even though you are \
                already using the new API, consider moving the calls to the same file or \
                suppressing the warning.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                StorageDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}