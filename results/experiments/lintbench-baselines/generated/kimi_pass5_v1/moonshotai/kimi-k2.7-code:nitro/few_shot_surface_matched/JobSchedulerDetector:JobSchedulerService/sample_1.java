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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String JOB_INFO_BUILDER = "android.app.job.JobInfo$Builder";
    private static final String JOB_SERVICE_CLASS = "android.app.job.JobService";
    private static final String COMPONENT_NAME_CLASS = "android.content.ComponentName";
    private static final String BIND_JOB_SERVICE_PERMISSION =
            "android.permission.BIND_JOB_SERVICE";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE =
            Issue.create(
                            "JobSchedulerService",
                            "JobScheduler service problems",
                            "When using `JobScheduler` the target service must extend `JobService`, "
                                    + "be declared in the manifest and require the "
                                    + "`android.permission.BIND_JOB_SERVICE` permission.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public JobSchedulerDetector() {}

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(JOB_INFO_BUILDER);
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {
        List<UExpression> arguments = node.getValueArguments();
        if (arguments.size() != 2) {
            return;
        }

        String className = getServiceClassName(context, arguments.get(1));
        if (className == null) {
            return;
        }

        PsiClass serviceClass = context.getEvaluator().findClass(className);
        if (serviceClass == null
                || !context.getEvaluator()
                        .extendsClass(serviceClass, JOB_SERVICE_CLASS, false)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "The service used with JobScheduler must extend `" + JOB_SERVICE_CLASS + "`");
            return;
        }

        context.getPartialResults(ISSUE).map().put(className, context.getLocation(node));
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull Detector.PartialResult partialResult) {
        Map<String, Object> map = partialResult.map();
        if (map.isEmpty()) {
            return;
        }

        Project project = context.getMainProject();
        Document manifest = project.getManifestDom();
        if (manifest == null) {
            return;
        }

        String packageName = project.getPackage();
        NodeList services = manifest.getElementsByTagName("service");

        Map<String, Element> manifestServices = new HashMap<>();
        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element) services.item(i);
            String name = service.getAttribute("android:name");
            if (name.isEmpty()) {
                name = service.getAttributeNS(ANDROID_URI, "name");
            }
            if (name.isEmpty()) {
                continue;
            }
            manifestServices.put(resolveClassName(packageName, name), service);
        }

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String className = entry.getKey();
            Object value = entry.getValue();
            if (!(value instanceof Location)) {
                continue;
            }
            Location location = (Location) value;

            Element service = manifestServices.get(className);
            if (service == null) {
                context.report(
                        ISSUE,
                        location,
                        "The JobScheduler service `" + className + "` must be registered in the manifest");
                continue;
            }

            String permission = service.getAttribute("android:permission");
            if (permission.isEmpty()) {
                permission = service.getAttributeNS(ANDROID_URI, "permission");
            }
            if (!BIND_JOB_SERVICE_PERMISSION.equals(permission)) {
                context.report(
                        ISSUE,
                        location,
                        "The JobScheduler service `"
                                + className
                                + "` must require the `"
                                + BIND_JOB_SERVICE_PERMISSION
                                + "` permission");
            }
        }
    }

    @Nullable
    private String getServiceClassName(@NonNull JavaContext context, @NonNull UExpression expression) {
        if (expression instanceof UClassLiteralExpression) {
            PsiType type = ((UClassLiteralExpression) expression).getType();
            return type != null ? type.getCanonicalText() : null;
        }

        if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            PsiType returnType = call.getExpressionType();
            String typeName = returnType != null ? returnType.getCanonicalText() : null;
            if (!COMPONENT_NAME_CLASS.equals(typeName)) {
                return null;
            }

            List<UExpression> args = call.getValueArguments();
            if (args.size() < 2) {
                return null;
            }

            UExpression second = args.get(1);
            if (second instanceof UClassLiteralExpression) {
                PsiType type = ((UClassLiteralExpression) second).getType();
                return type != null ? type.getCanonicalText() : null;
            }

            Object value = ConstantEvaluator.evaluate(context, second);
            if (value instanceof String) {
                return (String) value;
            }
        }

        return null;
    }

    @NonNull
    private String resolveClassName(@Nullable String packageName, @NonNull String name) {
        if (name.startsWith(".")) {
            return (packageName != null ? packageName : "") + name;
        }
        if (name.indexOf('.') == -1 && packageName != null) {
            return packageName + "." + name;
        }
        return name;
    }
}