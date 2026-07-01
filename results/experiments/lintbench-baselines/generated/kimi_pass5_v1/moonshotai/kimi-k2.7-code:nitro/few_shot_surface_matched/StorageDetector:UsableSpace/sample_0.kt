package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class StorageDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val USABLE_SPACE = Issue.create(
            id = "UsableSpace",
            briefDescription = "Using getUsableSpace() instead of getAllocatableBytes(UUID)",
            explanation = """
                When deciding if the device has enough disk space to hold your new data, call `StorageManager#getAllocatableBytes(UUID)` instead of using `File#getUsableSpace()`, since the former will consider any cached data that the system is willing to clear on your behalf.

                When you need to allocate disk space for large files, consider using `StorageManager#allocateBytes(FileDescriptor, long)`, which will automatically clear cached files belonging to other apps (as needed) to meet your request.

                Note that these methods require API level 26. If your app is running on older devices, you will probably need to use both APIs, conditionally switching on `Build.VERSION.SDK_INT`. Lint only looks in the same compilation unit to see if you are already using both APIs, so if it warns even though you are already using the new API, consider moving the calls to the same file or suppressing the warning.
            """,
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(StorageDetector::class.java, Scope.JAVA_FILE_SCOPE),
            androidSpecific = true,
        )
    }

    private val pendingReports = mutableListOf<PendingReport>()
    private var hasNewStorageApi = false

    private data class PendingReport(
        val node: UCallExpression,
        val message: String,
    )

    override fun beforeCheckFile(context: Context) {
        pendingReports.clear()
        hasNewStorageApi = false
    }

    override fun afterCheckFile(context: Context) {
        if (hasNewStorageApi || pendingReports.isEmpty()) return
        val javaContext = context as JavaContext
        for ((node, message) in pendingReports) {
            javaContext.report(
                Incident(USABLE_SPACE, node, javaContext.getLocation(node), message),
            )
        }
    }

    override fun getApplicableMethodNames() = listOf("getUsableSpace")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInSubClassOf(method, "java.io.File")) return
        pendingReports.add(
            PendingReport(
                node,
                "Using File#getUsableSpace() is discouraged; use StorageManager#getAllocatableBytes(UUID) instead",
            )
        )
    }

    override fun getApplicableCallExpressions() = listOf(UCallExpression::class.java)

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        val method = node.resolve() ?: return
        val name = method.name
        if ((name == "getAllocatableBytes" || name == "allocateBytes") &&
            context.evaluator.isMemberInSubClassOf(method, "android.os.storage.StorageManager")
        ) {
            hasNewStorageApi = true
        }
    }
}