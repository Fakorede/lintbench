package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
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

public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE_MISSING_ON_PLAY_FROM_SEARCH = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing `onPlayFromSearch`",
            "To support voice searches on Android Auto, in addition to adding an " +
            "`intent-filter` for the action `onPlayFromSearch`, " +
            "you also need to override and implement " +
            "`onPlayFromSearch(String query, Bundle bundle)`",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
    ).addMoreInfo("https://developer.android.com/training/auto/audio/index.html#support_voice");

    private static final String ON_PLAY_FROM_SEARCH = "onPlayFromSearch";

    private boolean mHasPlayFromSearchIntentFilter = false;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute("android:name");
        if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name)) {
            mHasPlayFromSearchIntentFilter = true;
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.support.v4.media.session.MediaSessionCompat.Callback",
                "android.media.session.MediaSession.Callback"
        );
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (!mHasPlayFromSearchIntentFilter) {
            return;
        }

        // Check if the class overrides onPlayFromSearch
        boolean hasOnPlayFromSearch = false;
        for (PsiMethod method : declaration.getMethods()) {
            if (ON_PLAY_FROM_SEARCH.equals(method.getName())) {
                hasOnPlayFromSearch = true;
                break;
            }
        }

        if (!hasOnPlayFromSearch) {
            context.report(
                    ISSUE_MISSING_ON_PLAY_FROM_SEARCH,
                    declaration,
                    context.getNameLocation(declaration),
                    "This class does not override `onPlayFromSearch`, which is required to " +
                    "support voice searches on Android Auto"
            );
        }
    }
}