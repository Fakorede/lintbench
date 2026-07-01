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
import org.jetbrains.uast.visitor.AbstractUastVisitor

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

        private val NEW_STORAGE_METHODS = setOf(GET_ALLOCATABLE_BYTES, ALLOCATE_BYTES)

        private const val STORAGE_MANAGER_CLASS = "android.os.storage.StorageManager"
        private const val FILE_CLASS = "java.io.File"
    }

    /**
     * Tracks whether the current file uses the new storage allocation APIs.
     */
    private var usesNewStorageApi = false

    /**
     * Pending reports: list of (node, message) pairs to report if we don't find new API usage.
     */
    private val pendingReports = mutableListOf<Pair<UCallExpression, String>>()

    override fun getApplicableMethodNames(): List<String> {
        return listOf(GET_USABLE_SPACE, GET_ALLOCATABLE_BYTES, ALLOCATE_BYTES)
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        if (methodName in NEW_STORAGE_METHODS) {
            // Check if it's on StorageManager
            if (context.evaluator.isMemberInClass(method, STORAGE_MANAGER_CLASS)) {
                usesNewStorageApi = true
            }
            return
        }

        if (methodName == GET_USABLE_SPACE) {
            // Check that it's called on a java.io.File instance
            if (!context.evaluator.isMemberInClass(method, FILE_CLASS)) {
                return
            }
            val message =
                "Consider using `getAllocatableBytes(UUID)` instead of `getUsableSpace()` " +
                    "since the former will consider any cached data that the system is " +
                    "willing to clear on your behalf"
            pendingReports.add(Pair(node, message))
        }
    }

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        // We use visitMethodCall for the actual logic; this override is present
        // as required by the specification but logic is handled in visitMethodCall.
    }

    override fun afterCheckFile(context: JavaContext) {
        if (!usesNewStorageApi) {
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
        usesNewStorageApi = false
        pendingReports.clear()
    }

    override fun beforeCheckFile(context: JavaContext) {
        // Scan the entire file first to detect if new APIs are used anywhere in the file
        usesNewStorageApi = false
        pendingReports.clear()

        val uFile = context.uastFile ?: return
        uFile.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val methodName = node.methodName ?: return false
                if (methodName in NEW_STORAGE_METHODS) {
                    val resolved = node.resolve()
                    if (resolved is PsiMethod &&
                        context.evaluator.isMemberInClass(resolved, STORAGE_MANAGER_CLASS)
                    ) {
                        usesNewStorageApi = true
                    }
                }
                return false
            }
        })
    }
}