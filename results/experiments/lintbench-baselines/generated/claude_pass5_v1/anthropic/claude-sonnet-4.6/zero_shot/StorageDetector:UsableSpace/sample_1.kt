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
import org.jetbrains.uast.UFile
import org.jetbrains.uast.visitor.AbstractUastVisitor

class StorageDetector : Detector(), Detector.UastScanner {

    companion object {
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
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(
                StorageDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val GET_USABLE_SPACE = "getUsableSpace"
        private const val GET_ALLOCATABLE_BYTES = "getAllocatableBytes"
        private const val ALLOCATE_BYTES = "allocateBytes"
        private const val STORAGE_MANAGER = "android.os.storage.StorageManager"
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(GET_USABLE_SPACE)
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.name != GET_USABLE_SPACE) {
            return
        }

        // Check if the file already uses the new APIs
        val uFile = context.uastFile ?: return
        if (usesNewStorageApi(uFile)) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getCallLocation(node, includeReceiver = true, includeArguments = true),
            "Consider also using `StorageManager#getAllocatableBytes` and " +
                "`StorageManager#allocateBytes` which will consider cached data that the " +
                "system is willing to clear on your behalf"
        )
    }

    private fun usesNewStorageApi(uFile: UFile): Boolean {
        var found = false
        uFile.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (found) return false
                val methodName = node.methodName
                if (methodName == GET_ALLOCATABLE_BYTES || methodName == ALLOCATE_BYTES) {
                    // Check if it's called on StorageManager
                    val resolved = node.resolve()
                    if (resolved != null) {
                        val containingClass = resolved.containingClass
                        if (containingClass != null &&
                            containingClass.qualifiedName == STORAGE_MANAGER
                        ) {
                            found = true
                        }
                    } else {
                        // If we can't resolve, assume it's the right one if name matches
                        found = true
                    }
                }
                return false
            }
        })
        return found
    }
}