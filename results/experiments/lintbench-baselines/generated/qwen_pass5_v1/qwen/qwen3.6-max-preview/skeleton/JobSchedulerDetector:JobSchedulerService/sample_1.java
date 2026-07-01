package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String PARTIAL_RESULT_KEY = "JobSchedulerServices";

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
        return Arrays.asList("android.app.job.JobInfo$Builder");
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

        UExpression componentNameArg = args.get(1);
        if (!(componentNameArg instanceof UCallExpression)) {
            return;
        }

        UCallExpression cnCall = (UCallExpression) componentNameArg;
        List<UExpression> cnArgs = cnCall.getValueArguments();
        if (cnArgs.size() < 2) {
            return;
        }

        UExpression classArg = cnArgs.get(1);
        PsiType type = classArg.getExpressionType();
        if (!(type instanceof PsiClassType)) {
            return;
        }

        PsiClass serviceClass = ((PsiClassType) type).resolve();
        if (serviceClass == null) {
            return;
        }

        if (!context.getEvaluator().extendsClass(serviceClass, "android.app.job.JobService", false)) {
            context.report(
                    ISSUE,
                    classArg,
                    context.getLocation(classArg),
                    "JobScheduler service class must extend android.app.job.JobService");
            return;
        }

        String fqn = serviceClass.getQualifiedName();
        if (fqn != null) {
            PartialResult<List<String>> result =
                    context.getPartialResults().getOrCreate(PARTIAL_RESULT_KEY, ArrayList::new);
            result.getData().add(fqn);
        }
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        List<String> services = partialResults.getData();
        if (services == null || services.isEmpty()) {
            return;
        }

        Document manifest = context.getMainProject().getMergedManifest();
        if (manifest == null) {
            return;
        }

        String pkg = context.getMainProject().getPackage();
        if (pkg == null) {
            pkg = "";
        }

        Set<String> registeredServices = new HashSet<>();
        Set<String> permittedServices = new HashSet<>();

        NodeList serviceNodes = manifest.getElementsByTagName("service");
        for (int i = 0; i < serviceNodes.getLength(); i++) {
            Element service = (Element) serviceNodes.item(i);
            String name = service.getAttributeNS(ANDROID_URI, "name");
            if (name == null || name.isEmpty()) {
                continue;
            }

            String fqn = name.startsWith(".") ? pkg + name : name;
            registeredServices.add(fqn);

            String permission = service.getAttributeNS(ANDROID_URI, "permission");
            if ("android.permission.BIND_JOB_SERVICE".equals(permission)) {
                permittedServices.add(fqn);
            }
        }

        Location manifestLocation = Location.create(context.getMainProject().getManifest());

        for (String fqn : services) {
            if (!registeredServices.contains(fqn)) {
                context.report(
                        ISSUE,
                        manifestLocation,
                        "JobScheduler service " + fqn + " is not registered in the manifest");
            } else if (!permittedServices.contains(fqn)) {
                context.report(
                        ISSUE,
                        manifestLocation,
                        "JobScheduler service " + fqn
                                + " must require android.permission.BIND_JOB_SERVICE");
            }
        }
    }
}