package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Manifest tag typo",
            "This check looks through the manifest, and if it finds any tags that look like likely misspellings, they are flagged.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
            "manifest", "application", "activity", "activity-alias", "service", "receiver",
            "provider", "uses-permission", "uses-permission-sdk-23", "uses-sdk", "uses-feature",
            "uses-library", "uses-configuration", "permission", "permission-group",
            "permission-tree", "instrumentation", "supports-screens", "compatible-screens",
            "supports-gl-texture", "intent-filter", "action", "category", "data", "meta-data",
            "grant-uri-permission", "path-permission", "profileable", "queries", "package",
            "intent", "screen", "layout", "nav-graph", "attribution", "property",
            "restrict-update", "original-package", "adopt-permissions", "eat-comment",
            "protected-broadcast", "preferred", "feature-group", "package-verifier", "uses-split"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(XmlScannerConstants.ALL);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getLocalName();
        if (tag == null) {
            tag = element.getNodeName();
        }
        if (tag == null || VALID_TAGS.contains(tag)) {
            return;
        }

        String suggestion = findClosestTag(tag);
        if (suggestion == null) {
            return;
        }

        String message = String.format(
                "Possible typo: '%1$s' is not a standard manifest tag; did you mean '%2$s'?",
                tag, suggestion);

        LintFix fix = fix().replace().text(tag).with(suggestion).build();

        context.report(ISSUE, context.getNameLocation(element), message, fix);
    }

    private static String findClosestTag(@NonNull String tag) {
        int length = tag.length();
        if (length < 2) {
            return null;
        }
        int threshold = length <= 4 ? 1 : 2;

        int bestDistance = Integer.MAX_VALUE;
        String bestTag = null;
        for (String valid : VALID_TAGS) {
            int distance = editDistance(tag, valid);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestTag = valid;
            }
            if (bestDistance == 0) {
                break;
            }
        }

        if (bestDistance <= threshold && bestDistance > 0) {
            return bestTag;
        }
        return null;
    }

    private static int editDistance(@NonNull String s, @NonNull String t) {
        int n = s.length();
        int m = t.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }

        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char sc = s.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = sc == t.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[m];
    }
}