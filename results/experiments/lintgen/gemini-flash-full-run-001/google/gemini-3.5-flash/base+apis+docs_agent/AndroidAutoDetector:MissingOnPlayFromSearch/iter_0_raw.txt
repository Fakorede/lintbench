package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing `onPlayFromSearch`",
            "To support voice searches on Android Auto, in addition to adding an " +
            "`intent-filter` for the action `onPlayFromSearch`, you also need to " +
            "override and implement `onPlayFromSearch(String query, Bundle bundle)`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.MANIFEST_AND_JAVA_FILES
            )
    );

    private boolean mHasPlayFromSearchIntent = false;
    private boolean mHasPlayFromSearchMethod = false;
    private final List<Location> mManifestLocations = new ArrayList<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasPlayFromSearchIntent = false;
        mHasPlayFromSearchMethod = false;
        mManifestLocations.clear();
    }

    // XmlScanner implementation
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name) || "onPlayFromSearch".equals(name)) {
            mHasPlayFromSearchIntent = true;
            mManifestLocations.add(context.getLocation(element));
        }
    }

    // SourceCodeScanner implementation
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod method) {
                if (isValidOnPlayFromSearchMethod(method)) {
                    mHasPlayFromSearchMethod = true;
                }
            }
        };
    }

    private boolean isValidOnPlayFromSearchMethod(UMethod method) {
        if (!"onPlayFromSearch".equals(method.getName())) {
            return false;
        }
        List<UParameter> parameters = method.getUastParameters();
        if (parameters.size() != 2) {
            return false;
        }
        String type1 = parameters.get(0).getType().getCanonicalText();
        String type2 = parameters.get(1).getType().getCanonicalText();
        return (type1.equals("java.lang.String") || type1.equals("String"))
            && (type2.equals("android.os.Bundle") || type2.equals("Bundle"));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mHasPlayFromSearchIntent && !mHasPlayFromSearchMethod) {
            for (Location location : mManifestLocations) {
                context.report(
                        ISSUE,
                        location,
                        "Missing `onPlayFromSearch` implementation in a `MediaSession.Callback`"
                );
            }
        }
    }
}