package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Typos in manifest tags",
            "This check looks through the manifest, and if it finds any tags " +
            "that look like likely misspellings, they are flagged.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    ManifestTypoDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    private static final Set<String> VALID_TAGS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "manifest", "application", "activity", "service", "receiver", "provider",
            "uses-permission", "permission", "permission-group", "permission-tree",
            "uses-sdk", "uses-configuration", "uses-feature", "supports-screens",
            "compatible-screens", "supports-gl-texture", "instrumentation",
            "original-package", "package-verifier", "queries", "uses-library",
            "intent-filter", "action", "category", "data", "meta-data",
            "activity-alias", "grant-uri-permission", "path-permission",
            "profileable", "property", "uses-native-library", "uses-permission-sdk-23"
    )));

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if (tagName == null || tagName.isEmpty() || tagName.contains(":")) {
            return;
        }

        if (VALID_TAGS.contains(tagName)) {
            return;
        }

        String closestMatch = null;
        int minDistance = Integer.MAX_VALUE;

        for (String validTag : VALID_TAGS) {
            int distance = getLevenshteinDistance(tagName, validTag);
            if (distance < minDistance) {
                minDistance = distance;
                closestMatch = validTag;
            }
        }

        // Only flag if it's a close match (distance <= 2 and distance is less than half the tag length)
        if (closestMatch != null && minDistance <= 2 && minDistance <= tagName.length() / 2) {
            String message = String.format("Possible typo in manifest tag: \"%s\". Did you mean \"%s\"?", tagName, closestMatch);
            LintFix fix = fix().replace().text(tagName).with(closestMatch).build();
            context.report(ISSUE, element, context.getNameLocation(element), message, fix);
        }
    }

    private static int getLevenshteinDistance(String s, String t) {
        if (s == null || t == null) {
            return Integer.MAX_VALUE;
        }
        int n = s.length();
        int m = t.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] p = new int[n + 1];
        int[] d = new int[n + 1];
        int[] _d;
        int i, j, cost;
        char tj;
        for (i = 0; i <= n; i++) p[i] = i;
        for (j = 1; j <= m; j++) {
            tj = t.charAt(j - 1);
            d[0] = j;
            for (i = 1; i <= n; i++) {
                cost = s.charAt(i - 1) == tj ? 0 : 1;
                d[i] = Math.min(Math.min(d[i - 1] + 1, p[i] + 1), p[i - 1] + cost);
            }
            _d = p; p = d; d = _d;
        }
        return p[n];
    }
}