package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ManifestTypoDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            ManifestTypoDetector.class,
            Scope.MANIFEST_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Manifest tag typo",
            "This check looks through the manifest, and if it finds any tags "
                    + "that look like likely misspellings, they are flagged.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            IMPLEMENTATION
    );

    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
            "manifest",
            "application",
            "activity",
            "activity-alias",
            "intent-filter",
            "action",
            "category",
            "data",
            "meta-data",
            "uses-permission",
            "uses-permission-sdk-23",
            "permission",
            "permission-group",
            "permission-tree",
            "uses-feature",
            "uses-library",
            "uses-native-library",
            "uses-sdk",
            "uses-split",
            "provider",
            "receiver",
            "service",
            "grant-uri-permission",
            "path-permission",
            "instrumentation",
            "supports-screens",
            "compatible-screens",
            "profile",
            "queries",
            "package",
            "intent",
            "attribution",
            "property"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String rawTag = element.getTagName();
        if (rawTag == null) {
            return;
        }

        String tag = rawTag.toLowerCase(Locale.US);
        if (VALID_TAGS.contains(tag)) {
            return;
        }

        String closest = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String validTag : VALID_TAGS) {
            int distance = editDistance(tag, validTag);
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = validTag;
            }
        }

        if (closest != null && bestDistance <= getThreshold(tag.length())) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    String.format("Tag '%1$s' looks like a misspelling of '%2$s'", rawTag, closest)
            );
        }
    }

    private static int getThreshold(int length) {
        if (length <= 4) {
            return 0;
        } else if (length <= 8) {
            return 1;
        } else {
            return 2;
        }
    }

    private static int editDistance(String s, String t) {
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
                char tc = t.charAt(j - 1);
                int cost = sc == tc ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[m];
    }
}