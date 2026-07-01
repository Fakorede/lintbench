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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing onPlayFromSearch",
                    "To support voice searches on Android Auto, in addition to adding an "
                            + "intent-filter for the action onPlayFromSearch, you also need to "
                            + "override and implement onPlayFromSearch(String query, Bundle bundle).",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            Scope.MANIFEST_SCOPE,
                            Scope.JAVA_FILE_SCOPE))
                    .setAndroidSpecific(true);

    private static final String ACTION_NAME = "onPlayFromSearch";
    private static final String ACTION_NAME_UNDERSCORE = "PLAY_FROM_SEARCH";
    private static final String ATTR_NAME = "android:name";
    private static final String TAG_ACTION = "action";
    private static final String TAG_INTENT_FILTER = "intent-filter";

    private static final String MEDIA_BROWSER_SERVICE = "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT =
            "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String MEDIA_BROWSER_SERVICE_COMPATX =
            "androidx.media.MediaBrowserServiceCompat";

    private static final String METHOD_NAME = "onPlayFromSearch";
    private static final String STRING_TYPE = "java.lang.String";
    private static final String BUNDLE_TYPE = "android.os.Bundle";

    private boolean mHasPlayFromSearchIntent;
    private final List<ServiceClass> mServiceClasses = new ArrayList<>();
    private final Set<PsiClass> mClassesWithMethod =
            Collections.newSetFromMap(new java.util.IdentityHashMap<PsiClass, Boolean>());

    private static class ServiceClass {
        final JavaContext context;
        final UClass declaration;

        ServiceClass(JavaContext context, UClass declaration) {
            this.context = context;
            this.declaration = declaration;
        }
    }

    public AndroidAutoDetector() {}

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasPlayFromSearchIntent = false;
        mServiceClasses.clear();
        mClassesWithMethod.clear();
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_ACTION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!TAG_ACTION.equals(element.getTagName())) {
            return;
        }
        Node parent = element.getParentNode();
        if (!(parent instanceof Element)
                || !TAG_INTENT_FILTER.equals(((Element) parent).getTagName())) {
            return;
        }
        String action = element.getAttribute(ATTR_NAME);
        if (action == null || action.isEmpty()) {
            return;
        }
        String lower = action.toLowerCase(Locale.ROOT);
        if (lower.contains(ACTION_NAME.toLowerCase(Locale.ROOT))
                || lower.contains(ACTION_NAME_UNDERSCORE.toLowerCase(Locale.ROOT))) {
            mHasPlayFromSearchIntent = true;
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                MEDIA_BROWSER_SERVICE, MEDIA_BROWSER_SERVICE_COMPAT, MEDIA_BROWSER_SERVICE_COMPATX);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        PsiClass psiClass = declaration.getPsi();
        if (psiClass == null) {
            return;
        }
        if (extendsMediaBrowserService(context, psiClass)) {
            mServiceClasses.add(new ServiceClass(context, declaration));
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(METHOD_NAME);
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod node) {
        PsiMethod method = node.getJavaPsi();
        if (method == null || !METHOD_NAME.equals(method.getName())) {
            return;
        }
        PsiParameter[] params = method.getParameterList().getParameters();
        if (params.length != 2) {
            return;
        }
        String p1 = params[0].getType().getCanonicalText();
        String p2 = params[1].getType().getCanonicalText();
        if (!STRING_TYPE.equals(p1) && !"String".equals(p1)) {
            return;
        }
        if (!BUNDLE_TYPE.equals(p2) && !"Bundle".equals(p2)) {
            return;
        }
        PsiClass containing = method.getContainingClass();
        if (containing == null) {
            return;
        }
        PsiClass serviceAncestor = getMediaBrowserServiceAncestor(context, containing);
        if (serviceAncestor != null) {
            mClassesWithMethod.add(serviceAncestor);
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (!mHasPlayFromSearchIntent) {
            return;
        }
        for (ServiceClass sc : mServiceClasses) {
            PsiClass psiClass = sc.declaration.getPsi();
            if (psiClass == null || mClassesWithMethod.contains(psiClass)) {
                continue;
            }
            sc.context.report(
                    ISSUE,
                    sc.declaration,
                    sc.context.getNameLocation(sc.declaration),
                    "You must override onPlayFromSearch(String, Bundle) in your "
                            + "MediaBrowserService to support voice searches on Android Auto.");
        }
    }

    private static boolean extendsMediaBrowserService(
            @NonNull JavaContext context, @NonNull PsiClass cls) {
        return context.getEvaluator().extendsClass(cls, MEDIA_BROWSER_SERVICE, false)
                || context.getEvaluator().extendsClass(cls, MEDIA_BROWSER_SERVICE_COMPAT, false)
                || context.getEvaluator().extendsClass(cls, MEDIA_BROWSER_SERVICE_COMPATX, false);
    }

    @Nullable
    private static PsiClass getMediaBrowserServiceAncestor(
            @NonNull JavaContext context, @Nullable PsiClass cls) {
        while (cls != null) {
            if (extendsMediaBrowserService(context, cls)) {
                return cls;
            }
            cls = cls.getContainingClass();
        }
        return null;
    }
}