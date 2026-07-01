package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler Service Problems",
            "JobScheduler problems: the service class must extend `JobService`, "
                    + "the service must be registered in the manifest and the registration "
                    + "must require the permission `android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    private final Set<String> mScheduledServices = new HashSet<>();
    private final Map<String, Location> mLocations = new HashMap<>();

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("android.app.job.JobInfo.Builder");
    }

    @Override
    public void visitConstructor(@NonNull JavaContext context, @NonNull UCallExpression call, @NonNull PsiMethod method) {
        List<UExpression> args = call.getValueArguments();
        if (args.size() != 2) {
            return;
        }

        UExpression compArg = args.get(1);
        PsiClass serviceClass = resolveServiceClass(context, compArg);
        if (serviceClass == null) {
            return;
        }

        String qualifiedName = serviceClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.extendsClass(serviceClass, "android.app.job.JobService", false)) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(call),
                    "JobScheduler service must extend android.app.job.JobService");
            return;
        }

        mScheduledServices.add(qualifiedName);
        mLocations.put(qualifiedName, context.getLocation(call));
    }

    @Nullable
    private PsiClass resolveServiceClass(@NonNull JavaContext context, @NonNull UExpression expr) {
        if (expr instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expr;
            List<UExpression> compArgs = call.getValueArguments();
            if (compArgs.size() == 2) {
                UExpression classArg = compArgs.get(1);
                if (classArg instanceof UClassLiteralExpression) {
                    return ((UClassLiteralExpression) classArg).getPsiType().resolve();
                }
                Object value = context.evaluate(classArg);
                if (value instanceof String) {
                    return context.findClass((String) value);
                }
            }
        } else if (expr instanceof UClassLiteralExpression) {
            return ((UClassLiteralExpression) expr).getPsiType().resolve();
        }
        return null;
    }

    @Override
    public void checkPartialResults(@NonNull Context context) {
        if (mScheduledServices.isEmpty()) {
            return;
        }

        File manifest = context.getMainProject().getManifest();
        if (manifest == null || !manifest.exists()) {
            return;
        }

        Document doc;
        try {
            doc = LintUtils.parseXml(manifest);
        } catch (Exception e) {
            return;
        }

        Element root = doc.getDocumentElement();
        if (root == null) {
            return;
        }

        NodeList apps = root.getElementsByTagName("application");
        if (apps.getLength() == 0) {
            return;
        }

        Element app = (Element) apps.item(0);
        NodeList services = app.getElementsByTagName("service");
        Set<String> registeredServices = new HashSet<>();
        Map<String, Boolean> permissionMap = new HashMap<>();
        String pkg = context.getMainProject().getPackage();
        if (pkg == null) {
            pkg = "";
        }

        for (int i = 0; i < services.getLength(); i++) {
            Element svc = (Element) services.item(i);
            String name = svc.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name != null && !name.isEmpty()) {
                String fullName = name;
                if (name.startsWith(".")) {
                    fullName = pkg + name;
                } else if (!name.contains(".")) {
                    fullName = pkg + "." + name;
                }
                registeredServices.add(fullName);
                String perm = svc.getAttributeNS("http://schemas.android.com/apk/res/android", "permission");
                permissionMap.put(fullName, "android.permission.BIND_JOB_SERVICE".equals(perm));
            }
        }

        for (String svcName : mScheduledServices) {
            Location loc = mLocations.get(svcName);
            if (loc == null) {
                continue;
            }

            if (!registeredServices.contains(svcName)) {
                context.report(ISSUE, loc,
                        "JobScheduler service " + svcName + " must be registered in the manifest");
            } else if (!Boolean.TRUE.equals(permissionMap.get(svcName))) {
                context.report(ISSUE, loc,
                        "JobScheduler service " + svcName + " must require android.permission.BIND_JOB_SERVICE in the manifest");
            }
        }
    }
}