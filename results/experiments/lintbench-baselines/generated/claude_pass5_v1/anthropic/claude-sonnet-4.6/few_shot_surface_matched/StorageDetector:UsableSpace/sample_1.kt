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
            implementation = Implementation(
                StorageDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true
        )

        private const val GET_USABLE_SPACE = "getUsableSpace"
        private const val GET_ALLOCATABLE_BYTES = "getAllocatableBytes"
        private const val ALLOCATE_BYTES = "allocateBytes"

        private val NEW_STORAGE_METHODS = setOf(GET_ALLOCATABLE_BYTES, ALLOCATE_BYTES)
    }

    /**
     * Whether the current file being analyzed uses the new storage allocation APIs.
     */
    private var usesNewStorageApi = false

    /**
     * Pending call expressions for getUsableSpace that we may need to report.
     */
    private val pendingReports = mutableListOf<Pair<UCallExpression, JavaContext>>()

    override fun getApplicableMethodNames(): List<String> {
        return listOf(GET_USABLE_SPACE, GET_ALLOCATABLE_BYTES, ALLOCATE_BYTES)
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        when {
            methodName == GET_USABLE_SPACE -> {
                // Check if the receiver is a java.io.File
                val evaluator = context.evaluator
                val containingClass = method.containingClass ?: return
                if (!evaluator.extendsClass(containingClass, "java.io.File", false)) {
                    return
                }
                pendingReports.add(Pair(node, context))
            }
            methodName in NEW_STORAGE_METHODS -> {
                // Check if this is StorageManager.getAllocatableBytes or StorageManager.allocateBytes
                val evaluator = context.evaluator
                val containingClass = method.containingClass ?: return
                if (evaluator.extendsClass(containingClass, "android.os.storage.StorageManager", false)) {
                    usesNewStorageApi = true
                }
            }
        }
    }

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        // This is called for every call expression; we use it to scan the whole file
        // for new storage API usage by traversing the file AST once per file.
        // The actual logic is handled in visitMethodCall.
    }

    override fun beforeCheckFile(context: JavaContext) {
        usesNewStorageApi = false
        pendingReports.clear()
    }

    override fun afterCheckFile(context: JavaContext) {
        // Also scan the entire file to detect new storage API usage in case
        // visitMethodCall ordering doesn't catch everything
        if (!usesNewStorageApi) {
            val uFile = context.uastFile ?: run {
                reportPending()
                return
            }
            uFile.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val methodName = node.methodName ?: return false
                    if (methodName in NEW_STORAGE_METHODS) {
                        val resolved = node.resolve()
                        if (resolved is PsiMethod) {
                            val containingClass = resolved.containingClass
                            if (containingClass != null &&
                                context.evaluator.extendsClass(
                                    containingClass,
                                    "android.os.storage.StorageManager",
                                    false
                                )
                            ) {
                                usesNewStorageApi = true
                            }
                        }
                    }
                    return false
                }
            })
        }

        reportPending()
    }

    private fun reportPending() {
        if (!usesNewStorageApi) {
            for ((node, context) in pendingReports) {
                val message =
                    "Consider also using `StorageManager#getAllocatableBytes` and " +
                        "`allocateBytes` which automatically clears cached files as needed"
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    message
                )
            }
        }
        pendingReports.clear()
        usesNewStorageApi = false
    }
}