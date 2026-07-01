package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Possible typo in Android manifest tag",
            "This check looks through the AndroidManifest.xml and flags tags that look "
                    + "like likely misspellings of valid manifest tags.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
            "action",
            "activity",
            "activity-alias",
            "adopt-permissions",
            "application",
            "category",
            "compatible-screens",
            "data",
            "eat-comment",
            "grant-uri-permission",
            "instrumentation",
            "intent",
            "intent-filter",
            "manifest",
            "meta-data",
            "original-package",
            "package",
            "path-permission",
            "permission",
            "permission-group",
            "permission-tree",
            "profileable",
            "property",
            "protected-broadcast",
            "provider",
            "queries",
            "receiver",
            "restrict-update",
            "service",
            "supports-gl-texture",
            "supports-screens",
            "uses-feature",
            "uses-library",
            "uses-native-library",
            "uses-permission",
            "uses-permission-sdk-23",
            "uses-sdk"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = getTagName(element).toLowerCase(Locale.US);
        if (VALID_TAGS.contains(tag)) {
            return;
        }

        String bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String valid : VALID_TAGS) {
            int distance = editDistance(tag, valid);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestMatch = valid;
            }
        }

        int threshold = tag.length() <= 4 ? 1 : 2;
        if (bestMatch != null && bestDistance > 0 && bestDistance <= threshold) {
            String message = String.format("Tag `%1$s` looks like a typo of `%2$s`", tag, bestMatch);
            Location location = context.getNameLocation(element);
            context.report(ISSUE, element, location, message);
        }
    }

    private static String getTagName(Element element) {
        String local = element.getLocalName();
        if (local != null && !local.isEmpty()) {
            return local;
        }
        String name = element.getTagName();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
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
                int cost = sc == t.charAt(j - 1) ? 0 : 1;
                int deletion = prev[j] + 1;
                int insertion = curr[j - 1] + 1;
                int substitution = prev[j - 1] + cost;
                curr[j] = Math.min(Math.min(deletion, insertion), substitution);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[m];
    }
}