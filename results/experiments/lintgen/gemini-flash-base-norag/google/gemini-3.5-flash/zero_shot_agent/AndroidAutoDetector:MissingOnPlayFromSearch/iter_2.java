package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Collection;

public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing `onPlayFromSearch`",
            "To support voice searches on Android Auto, in addition to adding an " +
                    "`intent-filter` for the action `onPlayFromSearch`, you also need to " +
                    "override and implement `onPlayFromSearch(String query, Bundle bundle)`",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
    );

    private static final String KEY_HAS_INTENT_FILTER = "AndroidAutoDetector.hasIntentFilter";
    private static final String KEY_INTENT_FILTER_LOCATION = "AndroidAutoDetector.intentFilterLocation";
    private static final String KEY_HAS_ON_PLAY_FROM_SEARCH = "AndroidAutoDetector.hasOnPlayFromSearch";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (name.isEmpty()) {
            name = element.getAttribute("android:name");
        }
        if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name)) {
            context.getProject().putProperty(KEY_HAS_INTENT_FILTER, Boolean.TRUE);
            context.getProject().putProperty(KEY_INTENT_FILTER_LOCATION, context.getLocation(element));
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                PsiMethod[] methods = node.findMethodsByName("onPlayFromSearch", true);
                for (PsiMethod method : methods) {
                    PsiParameterList parameterList = method.getParameterList();
                    if (parameterList.getParametersCount() == 2) {
                        PsiParameter[] parameters = parameterList.getParameters();
                        PsiType type1 = parameters[0].getType();
                        PsiType type2 = parameters[1].getType();
                        if (isStringType(type1) && isBundleType(type2)) {
                            context.getProject().putProperty(KEY_HAS_ON_PLAY_FROM_SEARCH, Boolean.TRUE);
                            break;
                        }
                    }
                }
            }
        };
    }

    private static boolean isStringType(@Nullable PsiType type) {
        if (type == null) return false;
        String canonical = type.getCanonicalText();
        return canonical.endsWith("String");
    }

    private static boolean isBundleType(@Nullable PsiType type) {
        if (type == null) return false;
        String canonical = type.getCanonicalText();
        return canonical.endsWith("Bundle");
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (context.getPhase() == 1) {
            Boolean hasIntentFilter = (Boolean) context.getProject().getProperty(KEY_HAS_INTENT_FILTER);
            Boolean hasOnPlayFromSearch = (Boolean) context.getProject().getProperty(KEY_HAS_ON_PLAY_FROM_SEARCH);
            Location location = (Location) context.getProject().getProperty(KEY_INTENT_FILTER_LOCATION);

            if (hasIntentFilter != null && hasIntentFilter && (hasOnPlayFromSearch == null || !hasOnPlayFromSearch) && location != null) {
                context.report(
                        ISSUE,
                        location,
                        "Missing `onPlayFromSearch` implementation in a `MediaSession.Callback` to support voice searches"
                );
            }
        }
    }
}