/**
 * StubGenerator — replaces Android Lint detector method bodies with minimal stubs.
 *
 * Preserves:
 *   - Package declaration, imports, class/object signatures
 *   - Companion object bodies verbatim (contains Issue.create() declarations
 *     that the test infrastructure needs to register issues)
 *   - Field / property declarations
 *
 * Stubs out:
 *   - Every method / function body → empty or typed-default return
 *     (e.g. `getApplicableMethodNames()` → `{ return null }` so lint
 *     skips calling any visit methods, producing zero warnings)
 *
 * Usage:
 *   stub-generator <src> <dst>
 *   stub-generator --manifest <file>   (tab-separated src\tdst lines, one per line)
 */

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.type.PrimitiveType
import com.github.javaparser.ast.type.VoidType
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import java.io.File

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

fun main(args: Array<String>) {
    when {
        args.size == 2 && args[0] == "--manifest" -> {
            // Warm up Kotlin env once before processing the batch
            @Suppress("UNUSED_EXPRESSION") kotlinEnv
            File(args[1]).forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val tab = line.indexOf('\t')
                if (tab < 0) { System.err.println("Bad manifest line: $line"); return@forEachLine }
                processFile(File(line.substring(0, tab)), File(line.substring(tab + 1)))
            }
        }
        args.size == 2 -> processFile(File(args[0]), File(args[1]))
        else -> {
            System.err.println("Usage: stub-generator <src> <dst>  |  --manifest <file>")
            System.exit(1)
        }
    }
}

fun processFile(src: File, dst: File) {
    val stub = try {
        when {
            src.name.endsWith(".kt")   -> stubKotlin(src)
            src.name.endsWith(".java") -> stubJava(src)
            else -> { System.err.println("Unknown extension: ${src.name}"); return }
        }
    } catch (e: Exception) {
        System.err.println("ERROR stubbing ${src.name}: ${e.message}")
        return
    }
    dst.parentFile?.mkdirs()
    dst.writeText(stub)
    System.err.println("  stubbed ${src.name}")
}

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

/** A pending source-text replacement (offsets are absolute, end is exclusive). */
private data class Replacement(val start: Int, val end: Int, val text: String)

/**
 * Apply [replacements] to [source], discarding any replacement whose range
 * is nested inside a larger replacement (the outer stub already removes the
 * inner body).  Replacements are applied back-to-front to preserve offsets.
 */
private fun applyReplacements(source: String, replacements: List<Replacement>): String {
    val deduped = replacements.filter { r ->
        replacements.none { other ->
            other !== r && other.start <= r.start && other.end >= r.end
        }
    }
    val sb = StringBuilder(source)
    deduped.sortedByDescending { it.start }
           .forEach { (s, e, t) -> sb.replace(s, e, t) }
    return sb.toString()
}

// ---------------------------------------------------------------------------
// Kotlin — uses kotlin-compiler-embeddable PSI
// ---------------------------------------------------------------------------

private val kotlinEnv: KotlinCoreEnvironment by lazy {
    val disposable = Disposer.newDisposable()
    val config = CompilerConfiguration().apply {
        put(
            CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
            PrintingMessageCollector(System.err, MessageRenderer.PLAIN_RELATIVE_PATHS, false)
        )
    }
    KotlinCoreEnvironment.createForProduction(
        disposable, config, EnvironmentConfigFiles.JVM_CONFIG_FILES
    )
}

fun stubKotlin(file: File): String {
    val source  = file.readText()
    val factory = KtPsiFactory(kotlinEnv.project)
    val ktFile  = factory.createFile(file.name, source)

    val replacements = mutableListOf<Replacement>()

    ktFile.forEachDescendantOfType<KtNamedFunction> { fn ->
        // Keep companion-object internals verbatim: Issue.create() declarations,
        // IMPLEMENTATION fields, etc. live there and must remain intact so the
        // test framework can register the issues.
        if (fn.isInsideCompanionObject()) return@forEachDescendantOfType

        val retType   = fn.typeReference?.text ?: ""
        val blockBody = fn.bodyBlockExpression                              // fun f() { … }
        val exprBody  = fn.bodyExpression?.takeIf { fn.bodyBlockExpression == null }  // fun f() = …

        when {
            blockBody != null ->
                replacements += Replacement(
                    blockBody.textRange.startOffset,
                    blockBody.textRange.endOffset,
                    blockStubKt(retType)
                )
            exprBody != null ->
                // Replace only the expression; the `=` token stays in place.
                replacements += Replacement(
                    exprBody.textRange.startOffset,
                    exprBody.textRange.endOffset,
                    exprStubKt(retType)
                )
        }
    }

    return applyReplacements(source, replacements)
}

/** Walk up the PSI parent chain to check containment in a companion object. */
private fun KtNamedFunction.isInsideCompanionObject(): Boolean {
    var node: PsiElement? = this.parent
    while (node != null) {
        if (node is KtObjectDeclaration && node.isCompanion()) return true
        node = node.parent
    }
    return false
}

/** Stub for a block-body function `{ … }`. */
private fun blockStubKt(returnType: String): String = when {
    returnType.isEmpty() || returnType == "Unit"                              -> "{ }"
    returnType.endsWith("?")                                                  -> "{ return null }"
    returnType == "Boolean"                                                   -> "{ return false }"
    returnType in setOf("Int", "Long", "Short", "Byte", "Double", "Float")   -> "{ return 0 }"
    else                                                                      -> "{ TODO() }"
}

/** Stub expression to replace `= <expr>` (the `=` token is preserved). */
private fun exprStubKt(returnType: String): String = when {
    returnType.isEmpty() || returnType == "Unit"                              -> "Unit"
    returnType.endsWith("?")                                                  -> "null"
    returnType == "Boolean"                                                   -> "false"
    returnType in setOf("Int", "Long", "Short", "Byte", "Double", "Float")   -> "0"
    else                                                                      -> "TODO()"
}

// ---------------------------------------------------------------------------
// Java — uses JavaParser
// ---------------------------------------------------------------------------

fun stubJava(file: File): String {
    val source = file.readText()
    val cu     = StaticJavaParser.parse(source)

    val replacements = mutableListOf<Replacement>()

    cu.findAll(MethodDeclaration::class.java).forEach { method ->
        val body  = method.body.orElse(null) ?: return@forEach  // abstract / interface
        val range = body.range.orElse(null)  ?: return@forEach
        val stub  = blockStubJava(method)

        val start = lineColToOffset(source, range.begin.line, range.begin.column)
        val end   = lineColToOffset(source, range.end.line,   range.end.column) + 1
        replacements += Replacement(start, end, stub)
    }

    return applyReplacements(source, replacements)
}

private fun blockStubJava(method: MethodDeclaration): String = when {
    method.type is VoidType      -> "{ }"
    method.type is PrimitiveType -> when ((method.type as PrimitiveType).type) {
        PrimitiveType.Primitive.BOOLEAN -> "{ return false; }"
        else                             -> "{ return 0; }"
    }
    else -> "{ return null; }"
}

/**
 * Convert a 1-based (line, column) position from JavaParser's [Range] into a
 * 0-based character offset in [source].
 */
private fun lineColToOffset(source: String, line: Int, col: Int): Int {
    var currentLine = 1
    var i = 0
    while (i < source.length && currentLine < line) {
        if (source[i] == '\n') currentLine++
        i++
    }
    return i + col - 1
}
