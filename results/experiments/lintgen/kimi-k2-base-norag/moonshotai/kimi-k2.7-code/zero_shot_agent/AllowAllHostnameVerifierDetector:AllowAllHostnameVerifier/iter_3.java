package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassScanner;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public final class AllowAllHostnameVerifierDetector extends Detector implements ClassScanner {

    private static final String HOSTNAME_VERIFIER = "javax/net/ssl/HostnameVerifier";
    private static final String X509_HOSTNAME_VERIFIER = "org/apache/http/conn/ssl/X509HostnameVerifier";

    private static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for use of HostnameVerifier implementations whose verify method "
                    + "always returns true (thus trusting any hostname) which could result in "
                    + "insecure network traffic caused by trusting arbitrary hostnames in "
                    + "TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE),
            "https://goo.gle/AllowAllHostnameVerifier"
    );

    @Override
    @Nullable
    public List<String> applicableAsmTypes() {
        return Arrays.asList(HOSTNAME_VERIFIER, X509_HOSTNAME_VERIFIER);
    }

    @Override
    public void checkClass(@NotNull JavaContext context, @NotNull ClassNode classNode) {
        if (classNode.interfaces == null
                || (!classNode.interfaces.contains(HOSTNAME_VERIFIER)
                        && !classNode.interfaces.contains(X509_HOSTNAME_VERIFIER))) {
            return;
        }

        for (MethodNode method : classNode.methods) {
            if (!"verify".equals(method.name)) {
                continue;
            }
            if (method.desc == null || !method.desc.endsWith("Z")) {
                continue;
            }

            boolean foundReturn = false;
            boolean allTrue = true;
            for (AbstractInsnNode insn = method.instructions.getFirst();
                    insn != null;
                    insn = insn.getNext()) {
                if (insn.getOpcode() == Opcodes.IRETURN) {
                    foundReturn = true;
                    if (!returnsTrue(insn.getPrevious())) {
                        allTrue = false;
                    }
                }
            }

            if (foundReturn && allTrue) {
                context.report(
                        ISSUE,
                        method,
                        context.getRangeLocation(method, 0, 0),
                        "Using a HostnameVerifier that accepts all hostnames is unsafe"
                );
            }
        }
    }

    private static boolean returnsTrue(@Nullable AbstractInsnNode insn) {
        while (insn != null) {
            int op = insn.getOpcode();
            if (op == Opcodes.ICONST_1) {
                return true;
            }
            if (op == Opcodes.ICONST_0) {
                return false;
            }
            if (op == -1 || op == Opcodes.NOP) {
                insn = insn.getPrevious();
                continue;
            }
            return false;
        }
        return false;
    }
}