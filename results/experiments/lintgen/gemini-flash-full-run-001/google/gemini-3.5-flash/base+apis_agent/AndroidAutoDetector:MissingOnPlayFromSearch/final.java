package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing `onPlayFromSearch`",
            "To support voice searches on Android Auto, in addition to adding an " +
            "`intent-filter` for the action `onPlayFromSearch`, you also need to override " +
            "and implement `onPlayFromSearch(String query, Bundle bundle)`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
    );

    private boolean mHavePlayFromSearchIntent;
    private boolean mHaveOnPlayFromSearchMethod;
    private Element mManifestElement;
    private XmlContext mManifestContext;

    @Override
    public void beforeCheckProject(@NotNull Context context) {
        mHavePlayFromSearchIntent = false;
        mHaveOnPlayFromSearchMethod = false;
        mManifestElement = null;
        mManifestContext = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name)) {
            mHavePlayFromSearchIntent = true;
            mManifestElement = element;
            mManifestContext = context;
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.media.session.MediaSession.Callback",
                "android.support.v4.media.session.MediaSessionCompat.Callback"
        );
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if ("onPlayFromSearch".equals(method.getName())) {
                mHaveOnPlayFromSearchMethod = true;
                break;
            }
        }
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        if (mHavePlayFromSearchIntent && !mHaveOnPlayFromSearchMethod) {
            if (mManifestContext != null && mManifestElement != null) {
                mManifestContext.report(
                        ISSUE,
                        mManifestElement,
                        mManifestContext.getLocation(mManifestElement),
                        "Missing `onPlayFromSearch` to support voice searches on Android Auto"
                );
            }
        }
    }
}