package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiMethod;

import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner, Detector.UastScanner {

    public static final Issue ISSUE_MISSING_ON_PLAY_FROM_SEARCH = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing `onPlayFromSearch`",
            "To support voice searches on Android Auto, in addition to adding an " +
            "`intent-filter` for the action `onPlayFromSearch`, " +
            "you also need to override and implement " +
            "`onPlayFromSearch(String query, Bundle bundle)`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
    ).addMoreInfo("https://developer.android.com/training/auto/audio/index.html#support_voice");

    private static final String MEDIA_SESSION_CALLBACK_CLASS =
            "android.media.session.MediaSession.Callback";
    private static final String MEDIA_COMPAT_SESSION_CALLBACK_CLASS =
            "android.support.v4.media.session.MediaSessionCompat.Callback";
    private static final String ON_PLAY_FROM_SEARCH = "onPlayFromSearch";
    private static final String ACTION_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String TAG_ACTION = "action";

    /** Whether the manifest declares the onPlayFromSearch intent action */
    private boolean mHasPlayFromSearchAction = false;

    /** Whether any MediaSession.Callback subclass implements onPlayFromSearch */
    private boolean mHasOnPlayFromSearchImplementation = false;

    /** The XML context where the action was found, for reporting */
    private XmlContext mActionXmlContext = null;
    private Element mActionElement = null;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_ACTION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "name");
        if (name == null || name.isEmpty()) {
            name = element.getAttribute("android:name");
        }
        if (ACTION_PLAY_FROM_SEARCH.equals(name)
                || "onPlayFromSearch".equals(name)) {
            mHasPlayFromSearchAction = true;
            mActionXmlContext = context;
            mActionElement = element;
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                MEDIA_SESSION_CALLBACK_CLASS,
                MEDIA_COMPAT_SESSION_CALLBACK_CLASS
        );
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // Check if this class overrides onPlayFromSearch
        PsiMethod[] methods = declaration.findMethodsByName(ON_PLAY_FROM_SEARCH, false);
        if (methods != null && methods.length > 0) {
            mHasOnPlayFromSearchImplementation = true;
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        if (mHasPlayFromSearchAction && !mHasOnPlayFromSearchImplementation) {
            if (mActionXmlContext != null && mActionElement != null) {
                mActionXmlContext.report(
                        ISSUE_MISSING_ON_PLAY_FROM_SEARCH,
                        mActionElement,
                        mActionXmlContext.getLocation(mActionElement),
                        "To support voice searches on Android Auto, you need to override and " +
                        "implement `onPlayFromSearch(String query, Bundle bundle)` in your " +
                        "`MediaSession.Callback`"
                );
            }
        }
    }
}