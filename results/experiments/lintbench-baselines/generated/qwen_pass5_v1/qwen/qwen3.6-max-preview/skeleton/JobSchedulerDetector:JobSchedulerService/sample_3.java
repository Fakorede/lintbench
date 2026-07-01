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
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jetbrains.uast.UCallExpression;
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
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String BIND_JOB_SERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE";
    private static final String JOB_SERVICE_CLASS = "android.app.job.JobService";

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
        Object evaluated = context.getEvaluator().evaluate(componentArg);
        if (!(evaluated instanceof android.content.ComponentName)) {
            return;
        }

        android.content.ComponentName componentName = (android.content.ComponentName) evaluated;
        String className = componentName.getClassName();

        PsiClass psiClass = context.findClass(className);
        if (psiClass == null) {
            return;
        }

        if (!context.getEvaluator().extendsClass(psiClass, JOB_SERVICE_CLASS, false)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "JobScheduler service must extend " + JOB_SERVICE_CLASS);
            return;
        }

        context.recordPartialResult(ISSUE, node, className);
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        File manifest = context.getProject().getManifest();
        if (manifest == null || !manifest.exists()) {
            return;
        }

        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(manifest);
            Element root = doc.getDocumentElement();
            if (root == null) {
                return;
            }

            NodeList services = root.getElementsByTagName("service");
            String pkg = context.getProject().getPackageName();

            for (Object data : partialResults.getData()) {
                String className = (String) data;
                boolean found = false;
                boolean hasPermission = false;

                for (int i = 0; i < services.getLength(); i++) {
                    Element service = (Element) services.item(i);
                    String name = service.getAttributeNS(ANDROID_URI, "name");
                    if (name != null) {
                        String fullName = name.startsWith(".") && pkg != null ? pkg + name : name;
                        if (className.equals(fullName) || className.equals(name)) {
                            found = true;
                            String perm = service.getAttributeNS(ANDROID_URI, "permission");
                            if (BIND_JOB_SERVICE_PERMISSION.equals(perm)) {
                                hasPermission = true;
                            }
                            break;
                        }
                    }
                }

                if (!found) {
                    context.report(
                            ISSUE,
                            Location.create(manifest),
                            "JobService " + className + " is not registered in the manifest");
                } else if (!hasPermission) {
                    context.report(
                            ISSUE,
                            Location.create(manifest),
                            "JobService " + className + " must require "
                                    + BIND_JOB_SERVICE_PERMISSION + " permission");
                }
            }
        } catch (Exception ignored) {
            // Ignore XML parsing errors; manifest issues are handled by other lint checks
        }
    }
}