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
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
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
                    "This check looks for various common mistakes in using the JobScheduler API: " +
                    "the service class must extend JobService, the service must be registered in the manifest " +
                    "and the registration must require the permission android.permission.BIND_JOB_SERVICE.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Set<String> servicesToCheck = new HashSet<>();

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
        if (args.size() < 2) {
            return;
        }

        UExpression componentArg = args.get(1);
        PsiClass serviceClass = findServiceClass(componentArg);

        if (serviceClass == null || serviceClass.getQualifiedName() == null) {
            return;
        }

        if (!context.getEvaluator().extendsClass(serviceClass, "android.app.job.JobService", false)) {
            context.report(ISSUE, node, context.getLocation(node),
                    "JobScheduler service class must extend android.app.job.JobService");
            return;
        }

        servicesToCheck.add(serviceClass.getQualifiedName());
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        if (servicesToCheck.isEmpty()) {
            return;
        }

        File manifest = context.getMainProject().getManifestFile();
        if (manifest == null || !manifest.exists()) {
            return;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(manifest);
            NodeList serviceNodes = doc.getElementsByTagName("service");

            Set<String> registeredServices = new HashSet<>();
            Set<String> permittedServices = new HashSet<>();
            String pkg = doc.getDocumentElement().getAttribute("package");

            for (int i = 0; i < serviceNodes.getLength(); i++) {
                Element service = (Element) serviceNodes.item(i);
                String name = service.getAttribute("android:name");
                if (name != null && !name.isEmpty()) {
                    if (name.startsWith(".")) {
                        name = pkg + name;
                    }
                    registeredServices.add(name);
                    String permission = service.getAttribute("android:permission");
                    if ("android.permission.BIND_JOB_SERVICE".equals(permission)) {
                        permittedServices.add(name);
                    }
                }
            }

            for (String serviceFqn : servicesToCheck) {
                if (!registeredServices.contains(serviceFqn)) {
                    context.report(ISSUE, Location.create(manifest),
                            "JobScheduler service " + serviceFqn + " is not registered in the manifest");
                } else if (!permittedServices.contains(serviceFqn)) {
                    context.report(ISSUE, Location.create(manifest),
                            "JobScheduler service " + serviceFqn + " must require android.permission.BIND_JOB_SERVICE permission");
                }
            }
        } catch (Exception ignored) {
            // Ignore XML parsing errors
        }
    }

    private PsiClass findServiceClass(UExpression expr) {
        if (expr instanceof UClassLiteralExpression) {
            return ((UClassLiteralExpression) expr).getPsi();
        }
        if (expr instanceof UCallExpression) {
            for (UExpression arg : ((UCallExpression) expr).getValueArguments()) {
                PsiClass cls = findServiceClass(arg);
                if (cls != null) {
                    return cls;
                }
            }
        }
        return null;
    }
}