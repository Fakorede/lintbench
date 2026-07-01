package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile

class StorageDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val FILE_CLASS = "java.io.File"
        private const val STORAGE_MANAGER = "android.os.storage.StorageManager"
        private const val GET_USABLE_SPACE = "getUsableSpace"
        private const val GET_ALLOCATABLE_BYTES = "getAllocatableBytes"
        private const val ALLOCATE_BYTES = "allocateBytes"

        private const val EXPLANATION = """When you need to allocate disk space for large files, consider using the new `allocateBytes(FileDescriptor, long)` API, which will automatically clear cached files belonging to other apps (as needed) to meet your request.

When deciding if the device has enough disk space to hold your new data, call `getAllocatableBytes(UUID)` instead of using `getUsableSpace()`, since the former will consider any cached data that the system is willing to clear on your behalf.

Note that these methods require API level 26. If your app is running on older devices, you will probably need to use both APIs, conditionally switching on `Build.VERSION.SDK_INT`. Lint only looks in the same compilation unit to see if you are already using both APIs, so if it warns even though you are already using the new API, consider moving the calls to the same file or suppressing the warning."""

        private const val MESSAGE = "Use StorageManager.getAllocatableBytes(UUID) instead of File.getUsableSpace(); when allocating space, prefer StorageManager.allocateBytes(FileDescriptor, long) on API 26+"

        private val IMPLEMENTATION = Implementation(
            StorageDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "UsableSpace",
            briefDescription = "Using getUsableSpace()",
            explanation = EXPLANATION,
            category = Category.PERFORMANCE,
            priority = 3,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf(GET_USABLE_SPACE)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (method.containingClass?.qualifiedName != FILE_CLASS) {
            return
        }

        if (hasNewStorageApi(context)) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            MESSAGE,
        )
    }

    private fun hasNewStorageApi(context: JavaContext): Boolean {
        val uFile = context.uastFile ?: return false
        return uFile.hasNewStorageApi()
    }

    private fun UElement.hasNewStorageApi(): Boolean {
        if (this is UCallExpression) {
            val name = methodName
            if (name == GET_ALLOCATABLE_BYTES || name == ALLOCATE_BYTES) {
                val containingClass = (resolve() as? PsiMethod)?.containingClass?.qualifiedName
                if (containingClass == STORAGE_MANAGER) {
                    return true
                }
            }
        }

        for (child in uastChildren) {
            if (child.hasNewStorageApi()) {
                return true
            }
        }

        return false
    }
}