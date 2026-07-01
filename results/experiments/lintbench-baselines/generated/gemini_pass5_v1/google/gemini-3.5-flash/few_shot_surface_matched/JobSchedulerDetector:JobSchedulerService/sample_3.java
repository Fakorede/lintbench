package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiClassType;
import java.io.File;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UClassLiteralExpression;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                            "JobSchedulerService",
                            "JobScheduler Problems",
                            "This check looks for various common mistakes in using the "
                                    + "JobScheduler API: the service class must extend `JobService`, "
                                    + "the service must be registered in the manifest and the registration "
                                    + "must require the permission `android.permission.BIND_JOB_SERVICE`.",
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
        if (!context.getEvaluator().isMemberInClass(constructor, "android.app.job.JobInfo.Builder")) {
            return;
        }
        List<UExpression> args = node.getValueArguments();
        if (args.size() < 2) {
            return;
        }
        UExpression componentNameArg = args.get(1);
        String serviceClassName = resolveServiceClass(componentNameArg);
        if (serviceClassName != null) {
            PsiClass psiClass = resolvePsiClass(componentNameArg);
            if (psiClass != null) {
                if (!context.getEvaluator().inheritsFrom(psiClass, "android.app.job.JobService", false)) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "The service class must extend `JobService`");
                }
            }
            LintMap map = context.getPartialResults(ISSUE).getMap();
            map.put(serviceClassName, context.file.getAbsolutePath());
        }
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context,
            @NonNull PartialResult partialResult) {
        if (partialResult.getIssue() != ISSUE) {
            return;
        }
        LintMap map = partialResult.getMap();
        if (map.keys().isEmpty()) {
            return;
        }
        org.w3c.dom.Document manifest = context.getMainProject().getMergedManifest();
        for (String serviceClassName : map.keys()) {
            String filePath = map.getString(serviceClassName);
            if (filePath == null) {
                continue;
            }
            File file = new File(filePath);
            Location location = Location.create(file);

            if (!isServiceRegistered(manifest, serviceClassName)) {
                context.report(
                        ISSUE,
                        location,
                        "The service " + serviceClassName + " must be registered in the manifest");
            } else {
                String permission = checkServicePermission(manifest, serviceClassName);
                if (!"android.permission.BIND_JOB_SERVICE".equals(permission)) {
                    context.report(
                            ISSUE,
                            location,
                            "The service "
                                    + serviceClassName
                                    + " must require the permission `android.permission.BIND_JOB_SERVICE`");
                }
            }
        }
    }

    @Nullable
    private String resolveServiceClass(UExpression expr) {
        if (expr instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expr;
            if (call.getReceiver() == null && "ComponentName".equals(call.getMethodName())) {
                List<UExpression> args = call.getValueArguments();
                if (args.size() == 2) {
                    UExpression secondArg = args.get(1);
                    if (secondArg instanceof UClassLiteralExpression) {
                        PsiType type = ((UClassLiteralExpression) secondArg).getType();
                        if (type instanceof PsiClassType) {
                            PsiClass psiClass = ((PsiClassType) type).resolve();
                            if (psiClass != null) {
                                return psiClass.getQualifiedName();
                            }
                        }
                    } else if (secondArg instanceof ULiteralExpression) {
                        Object value = ((ULiteralExpression) secondArg).getValue();
                        if (value instanceof String) {
                            return (String) value;
                        }
                    } else {
                        PsiType type = secondArg.getExpressionType();
                        if (type instanceof PsiClassType) {
                            PsiClass psiClass = ((PsiClassType) type).resolve();
                            if (psiClass != null) {
                                String qName = psiClass.getQualifiedName();
                                if (qName != null && !qName.equals("java.lang.Class")) {
                                    return qName;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private PsiClass resolvePsiClass(UExpression expr) {
        if (expr instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expr;
            if (call.getReceiver() == null && "ComponentName".equals(call.getMethodName())) {
                List<UExpression> args = call.getValueArguments();
                if (args.size() == 2) {
                    UExpression secondArg = args.get(1);
                    if (secondArg instanceof UClassLiteralExpression) {
                        PsiType type = ((UClassLiteralExpression) secondArg).getType();
                        if (type instanceof PsiClassType) {
                            return ((PsiClassType) type).resolve();
                        }
                    } else {
                        PsiType type = secondArg.getExpressionType();
                        if (type instanceof PsiClassType) {
                            return ((PsiClassType) type).resolve();
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isServiceRegistered(org.w3c.dom.Document manifest, String serviceClassName) {
        if (manifest == null) {
            return false;
        }
        org.w3c.dom.Element root = manifest.getDocumentElement();
        if (root == null) {
            return false;
        }
        org.w3c.dom.NodeList services = root.getElementsByTagName("service");
        for (int i = 0; i < services.getLength(); i++) {
            org.w3c.dom.Element service = (org.w3c.dom.Element) services.item(i);
            String name = service.getAttribute("android:name");
            if (serviceClassName.equals(name) || isMatchingClassName(serviceClassName, name)) {
                return true;
            }
        }
        return false;
    }

    private static String checkServicePermission(org.w3c.dom.Document manifest, String serviceClassName) {
        if (manifest == null) {
            return null;
        }
        org.w3c.dom.Element root = manifest.getDocumentElement();
        if (root == null) {
            return null;
        }
        org.w3c.dom.NodeList services = root.getElementsByTagName("service");
        for (int i = 0; i < services.getLength(); i++) {
            org.w3c.dom.Element service = (org.w3c.dom.Element) services.item(i);
            String name = service.getAttribute("android:name");
            if (serviceClassName.equals(name) || isMatchingClassName(serviceClassName, name)) {
                return service.getAttribute("android:permission");
            }
        }
        return null;
    }

    private static boolean isMatchingClassName(String fullClassName, String manifestName) {
        if (manifestName == null || fullClassName == null) {
            return false;
        }
        if (manifestName.startsWith(".")) {
            return fullClassName.endsWith(manifestName);
        }
        if (!manifestName.contains(".")) {
            return fullClassName.endsWith("." + manifestName);
        }
        return fullClassName.equals(manifestName);
    }
}