package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UReferenceExpression;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "JobSchedulerService",
                    "JobScheduler problems",
                    "This check looks for various common mistakes in using the JobScheduler API: "
                            + "the service class must extend JobService, the service must be registered "
                            + "in the manifest and the registration must require the permission "
                            + "android.permission.BIND_JOB_SERVICE.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final java.util.Set<String> checkedClasses =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    @Override
    public List<String> getApplicableConstructorTypes() {
        return java.util.Arrays.asList(
                "android.content.ComponentName",
                "android.app.job.JobInfo.Builder"
        );
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {
        PsiClass containingClass = constructor.getContainingClass();
        if (containingClass == null) {
            return;
        }
        String fqName = containingClass.getQualifiedName();
        if ("android.content.ComponentName".equals(fqName)) {
            List<UExpression> args = node.getValueArguments();
            if (args.size() == 2) {
                PsiClass targetClass = null;
                UExpression classArg = args.get(1);
                if (classArg instanceof UClassLiteralExpression) {
                    PsiType type = ((UClassLiteralExpression) classArg).getType();
                    if (type instanceof PsiClassType) {
                        targetClass = ((PsiClassType) type).resolve();
                    }
                } else {
                    Object evaluated = classArg.evaluate();
                    if (evaluated instanceof String) {
                        targetClass = context.getEvaluator().findClass((String) evaluated);
                    }
                }
                if (targetClass != null && extendsJobService(targetClass)) {
                    String targetClassName = targetClass.getQualifiedName();
                    if (targetClassName != null && checkedClasses.add(targetClassName)) {
                        checkManifestRegistration(context, node, targetClass);
                    }
                }
            }
        } else if ("android.app.job.JobInfo.Builder".equals(fqName)) {
            List<UExpression> args = node.getValueArguments();
            if (args.size() == 2) {
                UExpression compNameArg = args.get(1);
                PsiClass targetClass = resolveComponentNameClass(context, compNameArg);
                if (targetClass != null) {
                    if (!extendsJobService(targetClass)) {
                        context.report(
                                ISSUE,
                                compNameArg,
                                context.getLocation(compNameArg),
                                "The service class must extend JobService");
                    } else {
                        String targetClassName = targetClass.getQualifiedName();
                        if (targetClassName != null && checkedClasses.add(targetClassName)) {
                            checkManifestRegistration(context, node, targetClass);
                        }
                    }
                }
            }
        }
    }

    private boolean extendsJobService(PsiClass psiClass) {
        PsiClass current = psiClass;
        while (current != null) {
            String qualifiedName = current.getQualifiedName();
            if ("android.app.job.JobService".equals(qualifiedName)) {
                return true;
            }
            current = current.getSuperClass();
        }
        return false;
    }

    private PsiClass resolveComponentNameClass(@NonNull JavaContext context, @NonNull UExpression expression) {
        if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            PsiMethod method = call.resolve();
            if (method != null && method.isConstructor()) {
                PsiClass containingClass = method.getContainingClass();
                if (containingClass != null && "android.content.ComponentName".equals(containingClass.getQualifiedName())) {
                    List<UExpression> args = call.getValueArguments();
                    if (args.size() == 2) {
                        UExpression classArg = args.get(1);
                        if (classArg instanceof UClassLiteralExpression) {
                            PsiType type = ((UClassLiteralExpression) classArg).getType();
                            if (type instanceof PsiClassType) {
                                return ((PsiClassType) type).resolve();
                            }
                        } else {
                            Object evaluated = classArg.evaluate();
                            if (evaluated instanceof String) {
                                return context.getEvaluator().findClass((String) evaluated);
                            }
                        }
                    }
                }
            }
        } else if (expression instanceof UReferenceExpression) {
            PsiElement resolved = ((UReferenceExpression) expression).resolve();
            if (resolved instanceof PsiLocalVariable) {
                PsiLocalVariable local = (PsiLocalVariable) resolved;
                UExpression initializer = context.getUastContext().getInitializerBody(local);
                if (initializer != null) {
                    return resolveComponentNameClass(context, initializer);
                }
            } else if (resolved instanceof PsiField) {
                PsiField field = (PsiField) resolved;
                UExpression initializer = context.getUastContext().getInitializerBody(field);
                if (initializer != null) {
                    return resolveComponentNameClass(context, initializer);
                }
            }
        }
        return null;
    }

    private void checkManifestRegistration(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiClass targetClass) {
        String targetClassName = targetClass.getQualifiedName();
        if (targetClassName == null) {
            return;
        }

        org.w3c.dom.Document manifest = context.getProject().getMergedManifest();
        if (manifest == null) {
            return;
        }

        String packageName = manifest.getDocumentElement().getAttribute("package");
        if (packageName == null || packageName.isEmpty()) {
            packageName = context.getProject().getPackage();
        }
        if (packageName == null) {
            packageName = "";
        }

        org.w3c.dom.NodeList services = manifest.getElementsByTagName("service");
        boolean found = false;
        boolean hasPermission = false;

        for (int i = 0; i < services.getLength(); i++) {
            org.w3c.dom.Element serviceElement = (org.w3c.dom.Element) services.item(i);
            String name = serviceElement.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name.isEmpty()) {
                name = serviceElement.getAttribute("android:name");
            }

            String fullyQualifiedName = getFullyQualifiedName(name, packageName);
            if (targetClassName.equals(fullyQualifiedName)) {
                found = true;
                String permission = serviceElement.getAttributeNS("http://schemas.android.com/apk/res/android", "permission");
                if (permission.isEmpty()) {
                    permission = serviceElement.getAttribute("android:permission");
                }
                if ("android.permission.BIND_JOB_SERVICE".equals(permission)) {
                    hasPermission = true;
                }
                break;
            }
        }

        if (!found) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    String.format("The service %s must be registered in the manifest", targetClassName));
        } else if (!hasPermission) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    String.format("The service %s must require the permission android.permission.BIND_JOB_SERVICE", targetClassName));
        }
    }

    private static String getFullyQualifiedName(String name, String packageName) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        if (name.startsWith(".")) {
            return packageName + name;
        }
        if (!name.contains(".")) {
            return packageName + "." + name;
        }
        return name;
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        // Unused for this analysis, handled per module
    }
}