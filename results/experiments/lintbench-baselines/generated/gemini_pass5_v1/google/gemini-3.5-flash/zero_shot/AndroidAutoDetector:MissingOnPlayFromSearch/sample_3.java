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
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner, Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing `onPlayFromSearch`",
            "To support voice searches on Android Auto, in addition to adding an " +
            "intent-filter for the action `onPlayFromSearch`, you also need to " +
            "override and implement `onPlayFromSearch(String query, Bundle bundle)`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
    );

    private boolean mHasIntentFilter = false;
    private Location mIntentFilterLocation = null;
    private boolean mHaveOnPlayFromSearch = false;

    @Override
    public void beforeCheckProject(Context context) {
        mHasIntentFilter = false;
        mIntentFilterLocation = null;
        mHaveOnPlayFromSearch = false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name) || "onPlayFromSearch".equals(name)) {
            mHasIntentFilter = true;
            mIntentFilterLocation = context.getLocation(element);
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                if (context.getEvaluator().inheritsFrom(node, "android.media.session.MediaSession.Callback", false) ||
                    context.getEvaluator().inheritsFrom(node, "android.support.v4.media.session.MediaSessionCompat.Callback", false)) {
                    for (UMethod method : node.getMethods()) {
                        if ("onPlayFromSearch".equals(method.getName())) {
                            mHaveOnPlayFromSearch = true;
                            break;
                        }
                    }
                }
            }
        };
    }

    @Override
    public void afterCheckProject(Context context) {
        if (mHasIntentFilter && !mHaveOnPlayFromSearch && mIntentFilterLocation != null) {
            context.report(
                    ISSUE,
                    mIntentFilterLocation,
                    "Missing `onPlayFromSearch` to support voice searches on Android Auto"
            );
        }
    }
}