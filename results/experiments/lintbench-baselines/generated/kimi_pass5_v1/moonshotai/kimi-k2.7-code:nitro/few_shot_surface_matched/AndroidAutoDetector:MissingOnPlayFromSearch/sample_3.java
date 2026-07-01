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
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String ISSUE_ID = "MissingOnPlayFromSearch";
    private static final String ACTION_PLAY_FROM_SEARCH = "android.media.action.PLAY_FROM_SEARCH";
    private static final String ON_PLAY_FROM_SEARCH = "onPlayFromSearch";
    private static final String MEDIA_BROWSER_SERVICE = "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT =
            "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String MEDIA_BROWSER_SERVICE_ANDROIDX =
            "androidx.media.MediaBrowserServiceCompat";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "android:name";
    private static final String TYPE_STRING = "java.lang.String";
    private static final String TYPE_BUNDLE = "android.os.Bundle";

    private final List<String> mActionServices = new ArrayList<>();
    private final Map<String, ServiceInfo> mCandidateClasses = new HashMap<>();
    private final Set<String> mOverriddenClasses = new HashSet<>();

    private static class ServiceInfo {
        final JavaContext context;
        final UClass declaration;
        final String qualifiedName;

        ServiceInfo(JavaContext context, UClass declaration, String qualifiedName) {
            this.context = context;
            this.declaration = declaration;
            this.qualifiedName = qualifiedName;
        }
    }

    public static final Issue ISSUE =
            Issue.create(
                    ISSUE_ID,
                    "Missing onPlayFromSearch",
                    "To support voice searches on Android Auto, in addition to adding an "
                            + "intent-filter for the action android.media.action.PLAY_FROM_SEARCH, "
                            + "you also need to override and implement "
                            + "onPlayFromSearch(String query, Bundle bundle).",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class, Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE))
                    .setAndroidSpecific(true);

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mActionServices.clear();
        mCandidateClasses.clear();
        mOverriddenClasses.clear();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_ACTION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!TAG_ACTION.equals(element.getTagName()) && !TAG_ACTION.equals(element.getLocalName())) {
            return;
        }

        String actionName = element.getAttribute(ATTR_NAME);
        if (!ACTION_PLAY_FROM_SEARCH.equals(actionName)) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent == null
                || (!TAG_INTENT_FILTER.equals(parent.getNodeName())
                        && !TAG_INTENT_FILTER.equals(parent.getLocalName()))) {
            return;
        }

        Node serviceNode = parent.getParentNode();
        if (serviceNode == null
                || (!TAG_SERVICE.equals(serviceNode.getNodeName())
                        && !TAG_SERVICE.equals(serviceNode.getLocalName()))) {
            return;
        }

        if (!(serviceNode instanceof Element)) {
            return;
        }

        String className = ((Element) serviceNode).getAttribute(ATTR_NAME);
        if (className.isEmpty()) {
            return;
        }

        String packageName = context.getMainProject().getPackage();
        mActionServices.add(getQualifiedClassName(className, packageName));
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        List<String> classes = new ArrayList<>(3);
        classes.add(MEDIA_BROWSER_SERVICE);
        classes.add(MEDIA_BROWSER_SERVICE_COMPAT);
        classes.add(MEDIA_BROWSER_SERVICE_ANDROIDX);
        return classes;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        mCandidateClasses.put(qualifiedName, new ServiceInfo(context, declaration, qualifiedName));

        if (hasOnPlayFromSearch(declaration.getMethods())) {
            mOverriddenClasses.add(qualifiedName);
        }
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // Method calls are not used for this check; the override is verified in visitClass.
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (String serviceClass : mActionServices) {
            ServiceInfo info = mCandidateClasses.get(serviceClass);
            if (info == null || isOverridden(serviceClass)) {
                continue;
            }

            info.context.report(
                    ISSUE,
                    info.declaration,
                    info.context.getNameLocation(info.declaration),
                    "You must override and implement onPlayFromSearch(String, Bundle) to support "
                            + "voice searches on Android Auto");
        }
    }

    private boolean isOverridden(String qualifiedName) {
        if (mOverriddenClasses.contains(qualifiedName)) {
            return true;
        }

        ServiceInfo info = mCandidateClasses.get(qualifiedName);
        if (info == null) {
            return false;
        }

        for (UClass superClass : info.declaration.getSupers()) {
            String superName = superClass.getQualifiedName();
            if (superName != null && isOverridden(superName)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasOnPlayFromSearch(PsiMethod[] methods) {
        for (PsiMethod method : methods) {
            if (!ON_PLAY_FROM_SEARCH.equals(method.getName())) {
                continue;
            }
            if (method.getParameterList().getParametersCount() != 2) {
                continue;
            }
            PsiParameter[] params = method.getParameterList().getParameters();
            if (TYPE_STRING.equals(params[0].getType().getCanonicalText())
                    && TYPE_BUNDLE.equals(params[1].getType().getCanonicalText())) {
                return true;
            }
        }
        return false;
    }

    private static String getQualifiedClassName(String className, String packageName) {
        if (className.startsWith(".")) {
            return (packageName != null ? packageName : "") + className;
        }
        if (packageName != null
                && !packageName.isEmpty()
                && !className.contains(".")) {
            return packageName + "." + className;
        }
        return className;
    }
}