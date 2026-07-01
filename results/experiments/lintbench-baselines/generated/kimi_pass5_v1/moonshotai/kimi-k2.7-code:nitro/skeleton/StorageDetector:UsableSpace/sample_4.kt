package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class StorageDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val FILE_CLASS = "java.io.File"
        private const val STORAGE_MANAGER_CLASS = "android.os.storage.StorageManager"
        private const val METHOD_GET_USABLE_SPACE = "getUsableSpace"
        private const val METHOD_GET_ALLOCATABLE_BYTES = "getAllocatableBytes"
        private const val METHOD_ALLOCATE_BYTES = "allocateBytes"

        private val IMPLEMENTATION = Implementation(
            StorageDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "UsableSpace",
            briefDescription = "Using getUsableSpace()",
            explanation = """
                When you need to allocate disk space for large files, consider using the new
                `allocateBytes(FileDescriptor, long)` API, which will automatically clear
                cached files belonging to other apps (as needed) to meet your request.

                When deciding if the device has enough disk space to hold your new data,
                call `getAllocatableBytes(UUID)` instead of using `getUsableSpace()`, since
                the former will consider any cached data that the system is willing to
                clear on your behalf.

                Note that these methods require API level 26. If your app is running on
                older devices, you will probably need to use both APIs, conditionally
                switching on `Build.VERSION.SDK_INT`. Lint only looks in the same
                compilation unit to see if you are already using both APIs, so if it
                warns even though you are already using the new API, consider moving
                the calls to the same file or suppressing the warning.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 3,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    private val usableSpaceCalls = mutableListOf<Pair<JavaContext, UCallExpression>>()
    private var usesNewStorageApis = false

    override fun getApplicableMethodNames(): List<String> = listOf(
        METHOD_GET_USABLE_SPACE,
        METHOD_GET_ALLOCATABLE_BYTES,
        METHOD_ALLOCATE_BYTES,
    )

    protected override fun beforeCheckFile(context: Context) {
        usableSpaceCalls.clear()
        usesNewStorageApis = false
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        when (method.name) {
            METHOD_GET_USABLE_SPACE -> {
                if (method.containingClass?.qualifiedName == FILE_CLASS) {
                    usableSpaceCalls.add(context to node)
                }
            }
            METHOD_GET_ALLOCATABLE_BYTES, METHOD_ALLOCATE_BYTES -> {
                if (method.containingClass?.qualifiedName == STORAGE_MANAGER_CLASS) {
                    usesNewStorageApis = true
                }
            }
        }
    }

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        // No-op: all relevant calls are handled via visitMethodCall.
    }

    protected override fun afterCheckFile(context: Context) {
        if (usesNewStorageApis) {
            return
        }

        for ((callContext, node) in usableSpaceCalls) {
            callContext.report(
                ISSUE,
                node,
                callContext.getLocation(node),
                "Using `getUsableSpace()` to calculate available space; consider using `StorageManager.getAllocatableBytes()` instead",
            )
        }
    }
}