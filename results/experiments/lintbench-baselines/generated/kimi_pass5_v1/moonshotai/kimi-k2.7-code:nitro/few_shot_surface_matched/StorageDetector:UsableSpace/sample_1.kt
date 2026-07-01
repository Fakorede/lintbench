package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
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
        val USABLE_SPACE =
            Issue.create(
                id = "UsableSpace",
                briefDescription = "Using getUsableSpace() instead of getAllocatableBytes(UUID)",
                explanation =
                    """
                        When deciding if the device has enough disk space to hold your new data,
                        call `getAllocatableBytes(UUID)` instead of using `getUsableSpace()`, since
                        the former will consider any cached data that the system is willing to clear
                        on your behalf.

                        When you need to allocate disk space for large files, consider using the new
                        `allocateBytes(FileDescriptor, long)` API, which will automatically clear
                        cached files belonging to other apps (as needed) to meet your request.

                        Note that these methods require API level 26. If your app is running on older
                        devices, you will probably need to use both APIs, conditionally switching on
                        `Build.VERSION.SDK_INT`. Lint only looks in the same compilation unit to see
                        if you are already using both APIs, so if it warns even though you are already
                        using the new API, consider moving the calls to the same file or suppressing
                        the warning.
                    """,
                category = Category.PERFORMANCE,
                priority = 6,
                severity = Severity.WARNING,
                implementation = Implementation(StorageDetector::class.java, Scope.JAVA_FILE_SCOPE),
                androidSpecific = true,
            )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getUsableSpace")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "java.io.File")) {
            return
        }

        val message =
            "Use StorageManager#getAllocatableBytes(UUID) instead of File#getUsableSpace(); " +
                    "getAllocatableBytes accounts for cached data that the system may clear on your behalf."
        context.report(Incident(USABLE_SPACE, node, context.getLocation(node), message))
    }

    override fun visitCallExpression(node: UCallExpression) {
        // Detection is performed via getApplicableMethodNames() and visitMethodCall().
    }
}