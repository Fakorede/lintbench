package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.util.isMethodCall
import org.jetbrains.uast.visitor.AbstractUastVisitor

class StorageDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (!node.isMethodCall()) {
                    return
                }

                val method = node.resolve() ?: return
                if (!context.evaluator.isMemberInClass(method, "java.io.File")) {
                    return
                }
                if (method.name != "getUsableSpace") {
                    return
                }

                val file = context.uastFile ?: return
                if (usesStorageManagerApi(file, context)) {
                    return
                }

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Using `getUsableSpace()`; consider using `StorageManager.getAllocatableBytes(UUID)` instead"
                )
            }
        }

    private fun usesStorageManagerApi(file: UFile, context: JavaContext): Boolean {
        var found = false
        file.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val method = node.resolve()
                if (method != null) {
                    val name = method.name
                    if ((name == "getAllocatableBytes" || name == "allocateBytes") &&
                        context.evaluator.isMemberInClass(method, "android.os.storage.StorageManager")
                    ) {
                        found = true
                    }
                }
                return super.visitCallExpression(node)
            }
        })
        return found
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "UsableSpace",
            briefDescription = "Using getUsableSpace()",
            explanation = """
                When you need to allocate disk space for large files, consider using the new
                `allocateBytes(FileDescriptor, long)` API, which will automatically clear cached
                files belonging to other apps (as needed) to meet your request.

                When deciding if the device has enough disk space to hold your new data, call
                `getAllocatableBytes(UUID)` instead of using `getUsableSpace()`, since the former
                will consider any cached data that the system is willing to clear on your behalf.

                Note that these methods require API level 26. If your app is running on older
                devices, you will probably need to use both APIs, conditionally switching on
                `Build.VERSION.SDK_INT`. Lint only looks in the same compilation unit to see if
                you are already using both APIs, so if it warns even though you are already using
                the new API, consider moving the calls to the same file or suppressing the warning.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                StorageDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}