package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.*
import org.jetbrains.uast.*

class StorageDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            StorageDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

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
            category = Category.PERFORMANCE,
            priority = 3,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("getUsableSpace")
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubclassOf(method, "java.io.File", false)) {
            return
        }

        val uFile = context.uastFile
        if (uFile != null && containsAlternative(uFile)) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Avoid using `getUsableSpace()`; consider using `StorageManager.getAllocatableBytes(UUID)` or `StorageManager.allocateBytes(FileDescriptor, long)` which will automatically clear cached files if needed"
        )
    }

    private fun containsAlternative(element: UElement): Boolean {
        if (element is UCallExpression) {
            val name = element.methodName
            if (name == "getAllocatableBytes" || name == "allocateBytes") {
                return true
            }
        }
        for (child in element.uastChildren) {
            if (containsAlternative(child)) {
                return true
            }
        }
        return false
    }
}