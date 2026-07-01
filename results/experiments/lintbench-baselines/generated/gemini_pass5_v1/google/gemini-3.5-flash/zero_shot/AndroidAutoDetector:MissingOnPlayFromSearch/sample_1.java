package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.client.api.UElementHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    private static final Object KEY_INTENT_LOCATION = new Object();
    private static final Object KEY_HAS_CALLBACK = new Object();

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing `onPlayFromSearch`",
            "To support voice searches on Android Auto, in addition to adding an `intent-filter` " +
            "for the action `onPlayFromSearch`, you also need to override and implement " +
            "`onPlayFromSearch(String query, Bundle bundle)`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name) || "onPlayFromSearch".equals(name)) {
            context.getProject().putUserData(KEY_INTENT_LOCATION, context.getLocation(element));
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                for (UMethod method : node.getMethods()) {
                    if ("onPlayFromSearch".equals(method.getName())) {
                        List<UParameter> parameters = method.getUastParameters();
                        if (parameters.size() == 2) {
                            String type1 = parameters.get(0).getType().getCanonicalText();
                            String type2 = parameters.get(1).getType().getCanonicalText();
                            if (type1.contains("String") && type2.contains("Bundle")) {
                                context.getProject().putUserData(KEY_HAS_CALLBACK, Boolean.TRUE);
                            }
                        }
                    }
                }
            }
        };
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        if (context.getProject().equals(context.getMainProject())) {
            Location location = context.getProject().getUserData(KEY_INTENT_LOCATION);
            Boolean hasCallback = context.getProject().getUserData(KEY_HAS_CALLBACK);
            if (location != null && !Boolean.TRUE.equals(hasCallback)) {
                context.report(
                        ISSUE,
                        location,
                        "To support voice searches on Android Auto, you must override and implement `onPlayFromSearch(String query, Bundle bundle)`"
                );
            }
        }
    }
}