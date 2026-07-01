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
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UastUtils;

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

    @Override
    public List<String> getApplicableConstructorTypes() {
        return java.util.Collections.singletonList("android.app.job.JobInfo.Builder");
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {
        List<UExpression> args = node.getValueArguments();
        if (args.size() < 2) {
            return;
        }
        UExpression componentArg = args.get(1);
        PsiClass serviceClass = findServiceClass(context, componentArg);
        if (serviceClass == null) {
            return;
        }

        if (!context.getEvaluator().inheritsFrom(serviceClass, "android.app.job.JobService", false)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    String.format("The class %s must extend android.app.job.JobService", serviceClass.getQualifiedName()));
            return;
        }

        if (context.getProject().isLibrary()) {
            return;
        }

        String serviceClassName = serviceClass.getQualifiedName();
        if (serviceClassName == null) {
            return;
        }

        org.w3c.dom.Document manifest = context.getProject().getMergedManifest();
        if (manifest == null) {
            return;
        }

        org.w3c.dom.Element serviceElement = findServiceElement(manifest, serviceClassName);
        if (serviceElement == null) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    String.format("The service %s must be registered in the manifest", serviceClassName));
            return;
        }

        String permission = serviceElement.getAttributeNS("http://schemas.android.com/apk/res/android", "permission");
        if (permission == null || permission.isEmpty()) {
            permission = serviceElement.getAttribute("android:permission");
        }
        if (!"android.permission.BIND_JOB_SERVICE".equals(permission)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    String.format("The service %s must require android.permission.BIND_JOB_SERVICE permission", serviceClassName));
        }
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        // Handled directly in visitConstructor for compatibility and simplicity.
    }

    private PsiClass findServiceClass(JavaContext context, UExpression expression) {
        if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            PsiMethod constructor = call.resolve();
            if (constructor != null && constructor.isConstructor()) {
                PsiClass containingClass = constructor.getContainingClass();
                if (containingClass != null && "android.content.ComponentName".equals(containingClass.getQualifiedName())) {
                    List<UExpression> args = call.getValueArguments();
                    if (args.size() == 2) {
                        UExpression secondArg = args.get(1);
                        return resolveClassOfExpression(context, secondArg);
                    }
                }
            }
        } else if (expression instanceof UReferenceExpression) {
            PsiElement resolved = ((UReferenceExpression) expression).resolve();
            if (resolved instanceof PsiVariable) {
                PsiVariable variable = (PsiVariable) resolved;
                UExpression initializer = UastUtils.getInitializerFor(variable);
                if (initializer != null) {
                    return findServiceClass(context, initializer);
                }
            }
        }
        return null;
    }

    private PsiClass resolveClassOfExpression(JavaContext context, UExpression expression) {
        if (expression instanceof UClassLiteralExpression) {
            UClassLiteralExpression classLiteral = (UClassLiteralExpression) expression;
            PsiType type = classLiteral.getType();
            if (type instanceof PsiClassType) {
                PsiClassType classType = (PsiClassType) type;
                if (classType.getCanonicalText().startsWith("java.lang.Class")) {
                    PsiType[] parameters = classType.getParameters();
                    if (parameters.length == 1 && parameters[0] instanceof PsiClassType) {
                        return ((PsiClassType) parameters[0]).resolve();
                    }
                } else {
                    return classType.resolve();
                }
            }
        } else if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            if (value instanceof String) {
                return context.getEvaluator().findClass((String) value);
            }
        }
        PsiType type = expression.getExpressionType();
        if (type instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) type;
            String canonical = classType.getCanonicalText();
            if (canonical.startsWith("java.lang.Class") || canonical.startsWith("kotlin.reflect.KClass")) {
                PsiType[] parameters = classType.getParameters();
                if (parameters.length == 1 && parameters[0] instanceof PsiClassType) {
                    return ((PsiClassType) parameters[0]).resolve();
                }
            }
        }
        return null;
    }

    private org.w3c.dom.Element findServiceElement(org.w3c.dom.Document manifest, String serviceClassName) {
        String normalizedService = serviceClassName.replace('$', '.');
        org.w3c.dom.NodeList services = manifest.getElementsByTagName("service");
        for (int i = 0; i < services.getLength(); i++) {
            org.w3c.dom.Element service = (org.w3c.dom.Element) services.item(i);
            String name = service.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name == null || name.isEmpty()) {
                name = service.getAttribute("android:name");
            }
            if (name != null && !name.isEmpty()) {
                String normalizedName = name.replace('$', '.');
                if (normalizedName.startsWith(".")) {
                    String pkg = manifest.getDocumentElement().getAttribute("package");
                    if (pkg != null && !pkg.isEmpty()) {
                        if (normalizedService.equals(pkg + normalizedName)) {
                            return service;
                        }
                    }
                } else if (normalizedService.equals(normalizedName)) {
                    return service;
                } else if (!normalizedName.contains(".") && normalizedService.endsWith("." + normalizedName)) {
                    String pkg = manifest.getDocumentElement().getAttribute("package");
                    if (pkg != null && !pkg.isEmpty()) {
                        if (normalizedService.equals(pkg + "." + normalizedName)) {
                            return service;
                        }
                    }
                }
            }
        }
        return null;
    }
}