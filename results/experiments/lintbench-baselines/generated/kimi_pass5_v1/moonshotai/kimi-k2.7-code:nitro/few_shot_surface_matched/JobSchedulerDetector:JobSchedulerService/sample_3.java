package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String JOB_INFO_BUILDER = "android.app.job.JobInfo.Builder";
    private static final String JOB_SERVICE = "android.app.job.JobService";
    private static final String BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE";
    private static final String COMPONENT_NAME = "android.content.ComponentName";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE =
            Issue.create(
                            "JobSchedulerService",
                            "JobScheduler Service problems",
                            "When using the JobScheduler API the service class must extend "
                                    + "`android.app.job.JobService`, must be registered in the "
                                    + "manifest and the registration must require the "
                                    + "`android.permission.BIND_JOB_SERVICE` permission.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    private final Map<String, List<Location>> mScheduledServices = new HashMap<>();

    public JobSchedulerDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(JOB_INFO_BUILDER);
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

        String className = resolveServiceClass(context, args.get(1));
        if (className == null) {
            return;
        }

        PsiClass psiClass = context.getEvaluator().findClass(className);
        if (psiClass != null && !context.getEvaluator().extendsClass(psiClass, JOB_SERVICE, false)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "The scheduled service `" + className + "` must extend `android.app.job.JobService`");
            return;
        }

        if (psiClass != null) {
            mScheduledServices.computeIfAbsent(className, k -> new ArrayList<>()).add(context.getLocation(node));
        }
    }

    @Override
    public void checkPartialResults(@NonNull Context context, @NonNull Project project) {
        org.w3c.dom.Document manifest = context.getDriver().getMergedManifest(project);
        if (manifest == null) {
            mScheduledServices.clear();
            return;
        }

        org.w3c.dom.Element root = manifest.getDocumentElement();
        String packageName = root != null ? root.getAttribute("package") : "";
        org.w3c.dom.NodeList services = manifest.getElementsByTagName("service");

        for (Map.Entry<String, List<Location>> entry : mScheduledServices.entrySet()) {
            String className = entry.getKey();
            List<Location> locations = entry.getValue();

            boolean registered = false;
            boolean hasPermission = false;
            for (int i = 0; i < services.getLength(); i++) {
                org.w3c.dom.Element service = (org.w3c.dom.Element) services.item(i);
                String name = service.getAttributeNS(ANDROID_URI, "name");
                if (className.equals(resolveServiceName(name, packageName))) {
                    registered = true;
                    String permission = service.getAttributeNS(ANDROID_URI, "permission");
                    if (BIND_JOB_SERVICE.equals(permission)) {
                        hasPermission = true;
                    }
                    break;
                }
            }

            if (!registered) {
                for (Location location : locations) {
                    context.report(
                            ISSUE,
                            location,
                            "The scheduled service `" + className + "` must be registered in the manifest");
                }
            } else if (!hasPermission) {
                for (Location location : locations) {
                    context.report(
                            ISSUE,
                            location,
                            "The scheduled service `"
                                    + className
                                    + "` must require the `android.permission.BIND_JOB_SERVICE` permission");
                }
            }
        }

        mScheduledServices.clear();
    }

    @Nullable
    private String resolveServiceClass(@NonNull JavaContext context, @NonNull UExpression expression) {
        if (!(expression instanceof UCallExpression)) {
            return null;
        }

        UCallExpression call = (UCallExpression) expression;
        PsiMethod method = call.resolve();
        if (method == null
                || method.getContainingClass() == null
                || !COMPONENT_NAME.equals(method.getContainingClass().getQualifiedName())) {
            return null;
        }

        List<UExpression> args = call.getValueArguments();
        PsiParameter[] params = method.getParameterList().getParameters();
        if (params.length != 2 || args.size() < 2) {
            return null;
        }

        PsiType secondType = params[1].getType();
        UExpression secondArg = args.get(1);

        // ComponentName(Context, Class<?>) or ComponentName(String, Class<?>)
        if (secondType.equalsToText("java.lang.Class<?>")
                || secondType.equalsToText("java.lang.Class")) {
            if (secondArg instanceof UClassLiteralExpression) {
                PsiType type = ((UClassLiteralExpression) secondArg).getType();
                if (type instanceof PsiClassType) {
                    PsiClassType classType = (PsiClassType) type;
                    PsiClass psiClass = classType.resolve();
                    if (psiClass != null && "java.lang.Class".equals(psiClass.getQualifiedName())) {
                        PsiType[] parameters = classType.getParameters();
                        if (parameters.length == 1 && parameters[0] instanceof PsiClassType) {
                            PsiClass target = ((PsiClassType) parameters[0]).resolve();
                            return target != null ? target.getQualifiedName() : null;
                        }
                    } else if (psiClass != null) {
                        return psiClass.getQualifiedName();
                    }
                }
            }
            return null;
        }

        // ComponentName(String, String) or ComponentName(Context, String)
        if (secondType.equalsToText("java.lang.String")) {
            Object clsValue = ConstantEvaluator.evaluate(context, secondArg);
            if (clsValue instanceof String) {
                String cls = (String) clsValue;
                if (cls.contains(".")) {
                    return cls;
                }
                Object pkgValue = ConstantEvaluator.evaluate(context, args.get(0));
                if (pkgValue instanceof String) {
                    return pkgValue + "." + cls;
                }
            }
        }

        return null;
    }

    @NonNull
    private String resolveServiceName(@NonNull String name, @NonNull String packageName) {
        if (name.startsWith(".")) {
            return packageName + name;
        }
        if (!name.contains(".")) {
            return packageName + "." + name;
        }
        return name;
    }
}