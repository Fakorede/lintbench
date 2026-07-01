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

private const val JAVA_IO_FILE = "java.io.File"
private const val STORAGE_MANAGER = "android.os.storage.StorageManager"
private const val METHOD_GET_USABLE_SPACE = "getUsableSpace"
private const val METHOD_GET_ALLOCATABLE_BYTES = "getAllocatableBytes"
private const val METHOD_ALLOCATE_BYTES = "allocateBytes"

class StorageDetector : Detector(), SourceCodeScanner {

    override fun getApplicableCallNames(): List<String> = listOf(METHOD_GET_USABLE_SPACE)

    override fun visitCall(context: JavaContext, call: UCallExpression, method: PsiMethod?) {
        val containingClass = method?.containingClass?.qualifiedName
        if (containingClass != null && containingClass != JAVA_IO_FILE) {
            return
        }

        val uFile = context.uastFile
        if (uFile != null && fileUsesStorageManagerApi(uFile)) {
            return
        }

        context.report(
            ISSUE,
            call,
            context.getLocation(call),
            "Using $METHOD_GET_USABLE_SPACE()"
        )
    }

    private fun fileUsesStorageManagerApi(file: UFile): Boolean {
        var found = false
        file.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val name = node.methodName
                if (name == METHOD_GET_ALLOCATABLE_BYTES || name == METHOD_ALLOCATE_BYTES) {
                    val containingClass = node.resolve()?.containingClass?.qualifiedName
                    if (containingClass == STORAGE_MANAGER) {
                        found = true
                        return true
                    }
                }
                return false
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
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(StorageDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}