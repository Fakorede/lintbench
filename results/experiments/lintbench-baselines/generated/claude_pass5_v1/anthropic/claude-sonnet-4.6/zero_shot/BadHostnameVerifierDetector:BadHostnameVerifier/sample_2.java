/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;

import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Collections;
import java.util.List;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for implementations of `HostnameVerifier` whose `verify` method " +
            "always returns true (thus trusting any hostname) which could result in insecure " +
            "network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates " +
            "presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    BadHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE))
            .addMoreInfo("https://goo.gle/BadHostnameVerifier");

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String VERIFY_METHOD = "verify";

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                // Check if the class implements HostnameVerifier
                if (!implementsHostnameVerifier(context, node)) {
                    return;
                }

                // Look for the verify method
                for (UMethod method : node.getMethods()) {
                    if (isVerifyMethod(method)) {
                        checkVerifyMethod(context, method);
                    }
                }
            }
        };
    }

    private static boolean implementsHostnameVerifier(JavaContext context, UClass cls) {
        for (org.jetbrains.uast.UTypeReferenceExpression typeRef : cls.getUastSuperTypes()) {
            String qualifiedName = typeRef.getQualifiedName();
            if (HOSTNAME_VERIFIER.equals(qualifiedName)) {
                return true;
            }
        }
        // Also check via PSI
        com.intellij.psi.PsiClass psiClass = cls.getJavaPsi();
        if (psiClass != null) {
            for (com.intellij.psi.PsiClassType iface : psiClass.getImplementsListTypes()) {
                String name = iface.getCanonicalText();
                if (HOSTNAME_VERIFIER.equals(name)) {
                    return true;
                }
            }
            // Check superclass hierarchy
            if (context.getEvaluator().implementsInterface(psiClass, HOSTNAME_VERIFIER, false)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVerifyMethod(UMethod method) {
        if (!VERIFY_METHOD.equals(method.getName())) {
            return false;
        }
        // verify(String hostname, SSLSession session) returns boolean
        com.intellij.psi.PsiMethod psiMethod = method.getJavaPsi();
        if (psiMethod == null) {
            return false;
        }
        com.intellij.psi.PsiParameterList params = psiMethod.getParameterList();
        if (params.getParametersCount() != 2) {
            return false;
        }
        com.intellij.psi.PsiType returnType = psiMethod.getReturnType();
        if (returnType == null) {
            return false;
        }
        if (!returnType.equals(com.intellij.psi.PsiType.BOOLEAN)) {
            return false;
        }
        return true;
    }

    private static void checkVerifyMethod(JavaContext context, UMethod method) {
        // Visit the method body looking for return statements
        // If ALL return statements return the literal `true`, report the issue
        ReturnValueVisitor visitor = new ReturnValueVisitor();
        if (method.getUastBody() != null) {
            method.getUastBody().accept(visitor);
        }

        if (visitor.hasReturnTrue && !visitor.hasReturnFalse && !visitor.hasReturnNonLiteral) {
            context.report(
                    ISSUE,
                    method,
                    context.getNameLocation(method),
                    "`verify` always returns `true`, which could cause insecure network traffic " +
                    "due to trusting TLS/SSL server certificates for wrong hostnames");
        }
    }

    private static class ReturnValueVisitor extends AbstractUastVisitor {
        boolean hasReturnTrue = false;
        boolean hasReturnFalse = false;
        boolean hasReturnNonLiteral = false;

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            UExpression returnValue = node.getReturnExpression();
            if (returnValue == null) {
                // void return - shouldn't happen for boolean method
                return super.visitReturnExpression(node);
            }

            if (returnValue instanceof ULiteralExpression) {
                ULiteralExpression literal = (ULiteralExpression) returnValue;
                Object value = literal.getValue();
                if (Boolean.TRUE.equals(value)) {
                    hasReturnTrue = true;
                } else if (Boolean.FALSE.equals(value)) {
                    hasReturnFalse = true;
                } else {
                    hasReturnNonLiteral = true;
                }
            } else {
                // Check if the expression evaluates to a constant true
                // via constant evaluation
                Object evaluated = returnValue.evaluate();
                if (Boolean.TRUE.equals(evaluated)) {
                    hasReturnTrue = true;
                } else if (Boolean.FALSE.equals(evaluated)) {
                    hasReturnFalse = true;
                } else {
                    hasReturnNonLiteral = true;
                }
            }
            return super.visitReturnExpression(node);
        }

        // Don't descend into nested classes / lambdas
        @Override
        public boolean visitClass(UClass node) {
            return true; // skip
        }
    }
}