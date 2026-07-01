package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UFile

class StorageDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UsableSpace",
            briefDescription = "Using `getUsableSpace()`",
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
            """,
            category = Category.PERFORMANCE,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(StorageDetector::class.java, Scope.JAVA_FILE_SCOPE),
            androidSpecific = true,
        )

        private const val GET_USABLE_SPACE = "getUsableSpace"
        private const val GET_ALLOCATABLE_BYTES = "getAllocatableBytes"
        private const val ALLOCATE_BYTES = "allocateBytes"

        private const val STORAGE_MANAGER = "android.os.storage.StorageManager"
        private const val FILE_CLASS = "java.io.File"
    }

    /**
     * Whether the current file contains a call to the newer storage APIs
     * (getAllocatableBytes or allocateBytes).
     */
    private var usesNewApi = false

    /**
     * List of (node, message) pairs for getUsableSpace calls found in the current file,
     * to be reported after we've scanned the whole file.
     */
    private val pendingReports = mutableListOf<Pair<UCallExpression, String>>()

    override fun getApplicableMethodNames(): List<String> {
        return listOf(GET_USABLE_SPACE, GET_ALLOCATABLE_BYTES, ALLOCATE_BYTES)
    }

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        // Called for every call expression; we use visitMethodCall for our logic.
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        when {
            methodName == GET_ALLOCATABLE_BYTES &&
                    context.evaluator.isMemberInClass(method, STORAGE_MANAGER) -> {
                usesNewApi = true
            }
            methodName == ALLOCATE_BYTES &&
                    context.evaluator.isMemberInClass(method, STORAGE_MANAGER) -> {
                usesNewApi = true
            }
            methodName == GET_USABLE_SPACE &&
                    context.evaluator.isMemberInClass(method, FILE_CLASS) -> {
                val message =
                    "Consider using `getAllocatableBytes(UUID)` instead of `getUsableSpace()` " +
                            "since the former will consider any cached data that the system is " +
                            "willing to clear on your behalf"
                pendingReports.add(Pair(node, message))
            }
        }
    }

    override fun afterCheckFile(context: JavaContext) {
        if (!usesNewApi) {
            for ((node, message) in pendingReports) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    message,
                )
            }
        }
        // Reset state for next file
        usesNewApi = false
        pendingReports.clear()
    }
}