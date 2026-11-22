package net.minecraftforge.common.util;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.Restriction;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.stream.Collectors;

/** 把 Maven 版本和版本范围写成人能读的句子，用在加载错误提示里。 */
public final class MavenVersionStringHelper {
    private MavenVersionStringHelper() {
    }

    public static String artifactVersionToString(ArtifactVersion version) {
        return version.toString();
    }

    public static String versionRangeToString(VersionRange range) {
        return range.getRestrictions().stream().map(MavenVersionStringHelper::restrictionToString)
                .collect(Collectors.joining(", "));
    }

    public static String restrictionToString(Restriction restriction) {
        ArtifactVersion lower = restriction.getLowerBound();
        ArtifactVersion upper = restriction.getUpperBound();
        if (lower == null && upper == null) {
            return "any";
        }
        if (lower != null && upper != null) {
            if (artifactVersionToString(lower).equals(artifactVersionToString(upper))) {
                return artifactVersionToString(lower);
            }
            String l = artifactVersionToString(lower);
            String u = artifactVersionToString(upper);
            if (restriction.isLowerBoundInclusive() && restriction.isUpperBoundInclusive()) {
                return "between " + l + " and " + u + " (inclusive)";
            } else if (restriction.isLowerBoundInclusive()) {
                return l + " or above, and below " + u;
            } else if (restriction.isUpperBoundInclusive()) {
                return "above " + l + ", and " + u + " or below";
            }
            return "between " + l + " and " + u + " (exclusive)";
        }
        if (lower != null) {
            return restriction.isLowerBoundInclusive() ? artifactVersionToString(lower) + " or above"
                    : "above " + artifactVersionToString(lower);
        }
        return restriction.isUpperBoundInclusive() ? artifactVersionToString(upper) + " or below"
                : "below " + artifactVersionToString(upper);
    }

    /** 消息格式里的 {@code {N,vr}}。 */
    public static void parseVersionRange(StringBuffer stringBuffer, Object range) {
        stringBuffer.append(range instanceof VersionRange vr ? versionRangeToString(vr) : String.valueOf(range));
    }

    /** 消息格式里的 {@code {N,featurebound}}：特性要求的数值或版本范围。 */
    public static void parseFeatureBoundValue(StringBuffer stringBuffer, Object range) {
        if (range instanceof VersionRange vr) {
            stringBuffer.append(versionRangeToString(vr));
        } else {
            stringBuffer.append(range);
        }
    }
}
