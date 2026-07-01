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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "JobSchedulerService",
                    "JobScheduler problems",
                    "JobScheduler services must extend JobService, be registered in the manifest, " +
                    "and require the BIND_JOB_SERVICE permission.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String JOB_SERVICE = "android.app.job.JobService";
    private static final String BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE";
    private static final String KEY_SERVICES = "job_services";

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("android.app.job.JobInfo$Builder");
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {
        List<UExpression> args = node.getValueArguments();
        if (args.size() < 2) return;

        UExpression componentNameArg = args.get(1);
        if (!(componentNameArg instanceof UCallExpression)) return;

        UCallExpression cnCall = (UCallExpression) componentNameArg;
        List<UExpression> cnArgs = cnCall.getValueArguments();
        if (cnArgs.size() < 2) return;

        UExpression classArg = cnArgs.get(1);
        if (!(classArg instanceof UClassLiteralExpression)) return;

        PsiType type = ((UClassLiteralExpression) classArg).getType();
        if (!(type instanceof PsiClassType)) return;

        PsiClass psiClass = ((PsiClassType) type).resolve();
        if (psiClass == null) return;

        String fqn = psiClass.getQualifiedName();
        if (fqn == null) return;

        if (!context.getEvaluator().extendsClass(psiClass, JOB_SERVICE, false)) {
            context.report(ISSUE, node, context.getLocation(node),
                    "Job service must extend " + JOB_SERVICE);
            return;
        }

        context.requestRepeat();
        context.getPartialResults().add(KEY_SERVICES, fqn);
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        Collection<String> services = partialResults.getResults(KEY_SERVICES);
        if (services == null || services.isEmpty()) return;

        File manifest = context.getProject().getManifest();
        if (manifest == null || !manifest.exists()) return;

        String manifestContent = readFile(manifest);
        if (manifestContent == null) return;

        String pkg = extractAttribute(manifestContent, "manifest", "package");

        for (String serviceFqn : services) {
            boolean found = false;
            boolean hasPermission = false;

            int searchPos = 0;
            while (true) {
                int svcStart = manifestContent.indexOf("<service", searchPos);
                if (svcStart == -1) break;
                int svcEnd = manifestContent.indexOf(">", svcStart);
                if (svcEnd == -1) break;
                searchPos = svcEnd + 1;

                String svcTag = manifestContent.substring(svcStart, svcEnd);
                String name = extractAttribute(svcTag, "service", "name");
                if (name == null || name.isEmpty()) continue;

                String resolvedName = name;
                if (name.startsWith(".")) {
                    resolvedName = pkg + name;
                } else if (!name.contains(".")) {
                    resolvedName = pkg + "." + name;
                }

                if (serviceFqn.equals(resolvedName)) {
                    found = true;
                    String perm = extractAttribute(svcTag, "service", "permission");
                    if (BIND_JOB_SERVICE.equals(perm)) {
                        hasPermission = true;
                    }
                    break;
                }
            }

            if (!found) {
                context.report(ISSUE, Location.create(manifest),
                        "Job service " + serviceFqn + " is not registered in the manifest");
            } else if (!hasPermission) {
                context.report(ISSUE, Location.create(manifest),
                        "Job service " + serviceFqn + " must require permission " + BIND_JOB_SERVICE);
            }
        }
    }

    private static String readFile(File file) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }

    private static String extractAttribute(String xmlFragment, String tag, String attr) {
        int tagIdx = xmlFragment.indexOf(tag);
        if (tagIdx == -1) return null;
        String search = "android:" + attr + "=\"";
        int attrIdx = xmlFragment.indexOf(search, tagIdx);
        if (attrIdx == -1) {
            search = attr + "=\"";
            attrIdx = xmlFragment.indexOf(search, tagIdx);
        }
        if (attrIdx == -1) return null;
        int start = attrIdx + search.length();
        int end = xmlFragment.indexOf('"', start);
        if (end == -1) return null;
        return xmlFragment.substring(start, end).trim();
    }
}