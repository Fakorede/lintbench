package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
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

import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    private static final String ACTION_NAME = "onPlayFromSearch";
    private static final String METHOD_NAME = "onPlayFromSearch";

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing onPlayFromSearch implementation",
            "To support voice searches on Android Auto, you must declare an intent-filter "
                    + "action named `" + ACTION_NAME + "` and override `"
                    + METHOD_NAME + "(String query, Bundle bundle)` in your media session callback.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
    );

    private boolean mHasOnPlayFromSearchIntentFilter;
    private boolean mHasOnPlayFromSearchImplementation;
    private Location mActionLocation;

    @Override
    public void beforeCheckEachProject(Context context) {
        mHasOnPlayFromSearchIntentFilter = false;
        mHasOnPlayFromSearchImplementation = false;
        mActionLocation = null;
    }

    @Override
    public void afterCheckEachProject(Context context) {
        if (mHasOnPlayFromSearchIntentFilter
                && !mHasOnPlayFromSearchImplementation
                && mActionLocation != null) {
            context.report(
                    ISSUE,
                    mActionLocation,
                    "The manifest declares the `" + ACTION_NAME + "` action, but no `"
                            + METHOD_NAME + "(String, Bundle)` override was found."
            );
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String actionName = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "name");
        if (ACTION_NAME.equals(actionName)) {
            mHasOnPlayFromSearchIntentFilter = true;
            mActionLocation = context.getLocation(element);
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
                if (!METHOD_NAME.equals(node.getName())) {
                    return;
                }

                List<UParameter> parameters = node.getUastParameters();
                if (parameters.size() != 2) {
                    return;
                }

                if ("java.lang.String".equals(parameters.get(0).getType().getCanonicalText())
                        && "android.os.Bundle".equals(parameters.get(1).getType().getCanonicalText())) {
                    mHasOnPlayFromSearchImplementation = true;
                }
            }
        };
    }
}