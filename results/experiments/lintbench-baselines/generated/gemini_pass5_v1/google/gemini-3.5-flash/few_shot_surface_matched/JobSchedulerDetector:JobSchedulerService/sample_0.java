package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                            "JobSchedulerService",
                            "JobScheduler problems",
                            "This check looks for various common mistakes in using the JobScheduler API: "
                                    + "the service class must extend `JobService`, the service must be registered "
                                    + "in the manifest and the registration must require the permission "
                                    + "`android.permission.BIND_JOB_SERVICE`.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public JobSchedulerDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("android.app.job.JobInfo.Builder");
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {
        List<UExpression> args = node.getValueArguments();
        if (args.size() != 2) {
            return;
        }
        UExpression componentNameArg = args.get(1);
        if (componentNameArg instanceof UCallExpression) {
            UCallExpression componentNameCall = (UCallExpression) componentNameArg;
            PsiMethod compConstructor = componentNameCall.resolve();
            if (compConstructor != null
                    && compConstructor.getContainingClass() != null
                    && "android.content.ComponentName".equals(
                            compConstructor.getContainingClass().getQualifiedName())) {
                List<UExpression> compArgs = componentNameCall.getValueArguments();
                if (compArgs.size() == 2) {
                    UExpression classArg = compArgs.get(1);
                    PsiClass serviceClass = null;
                    if (classArg instanceof UClassLiteralExpression) {
                        PsiType type = ((UClassLiteralExpression) classArg).getType();
                        if (type instanceof PsiClassType) {
                            PsiType[] parameters = ((PsiClassType) type).getParameters();
                            if (parameters.length > 0 && parameters[0] instanceof PsiClassType) {
                                serviceClass = ((PsiClassType) parameters[0]).resolve();
                            }
                        }
                    } else {
                        Object evaluated = classArg.evaluate();
                        if (evaluated instanceof String) {
                            serviceClass = context.getEvaluator().findClass((String) evaluated);
                        } else {
                            PsiType type = classArg.getExpressionType();
                            if (type instanceof PsiClassType) {
                                PsiType[] parameters = ((PsiClassType) type).getParameters();
                                if (parameters.length > 0 && parameters[0] instanceof PsiClassType) {
                                    serviceClass = ((PsiClassType) parameters[0]).resolve();
                                }
                            }
                        }
                    }

                    if (serviceClass != null) {
                        String className = serviceClass.getQualifiedName();
                        if (className != null) {
                            if (!context.getEvaluator()
                                    .extendsClass(serviceClass, "android.app.job.JobService", false)) {
                                context.report(
                                        ISSUE,
                                        classArg,
                                        context.getLocation(classArg),
                                        "Scheduled service must extend `android.app.job.JobService`");
                            } else {
                                PartialResult partialResult = context.getPartialResults(ISSUE);
                                if (partialResult != null) {
                                    partialResult.map().put(className, true);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void checkPartialResults(@NonNull Context context, @NonNull PartialResult partialResult) {
        Document mergedManifest = context.getProject().getMergedManifest();
        if (mergedManifest == null || mergedManifest.getDocumentElement() == null) {
            return;
        }

        String pkg = mergedManifest.getDocumentElement().getAttribute("package");
        NodeList services = mergedManifest.getElementsByTagName("service");
        java.util.Map<String, Element> registeredServices = new java.util.HashMap<>();

        for (int i = 0; i < services.getLength(); i++) {
            Element serviceElement = (Element) services.item(i);
            String name = serviceElement.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name.isEmpty()) {
                name = serviceElement.getAttribute("android:name");
            }
            if (name.startsWith(".")) {
                name = pkg + name;
            } else if (!name.contains(".") && !pkg.isEmpty()) {
                name = pkg + "." + name;
            }
            registeredServices.put(name, serviceElement);
        }

        Location fallbackLocation = null;
        List<java.io.File> manifestFiles = context.getProject().getManifestFiles();
        if (!manifestFiles.isEmpty()) {
            fallbackLocation = Location.create(manifestFiles.get(0));
        } else {
            fallbackLocation = Location.create(context.getProject().getDir());
        }

        LintMap map = partialResult.map();
        for (String className : map.keys()) {
            Element serviceElement = registeredServices.get(className);
            if (serviceElement == null) {
                context.report(
                        ISSUE,
                        fallbackLocation,
                        "Service " + className + " must be registered in the manifest");
            } else {
                String permission = serviceElement.getAttributeNS("http://schemas.android.com/apk/res/android", "permission");
                if (permission.isEmpty()) {
                    permission = serviceElement.getAttribute("android:permission");
                }
                if (!"android.permission.BIND_JOB_SERVICE".equals(permission)) {
                    context.report(
                            ISSUE,
                            fallbackLocation,
                            "Service " + className + " must require the permission `android.permission.BIND_JOB_SERVICE`");
                }
            }
        }
    }
}