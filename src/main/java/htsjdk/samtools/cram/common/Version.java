package htsjdk.samtools.cram.common;

import java.util.Objects;

/**
 * A class to represent a version information, 3 number: major, minor and build number.
 */
public class Version implements Comparable<Version> {
    public final int major;
    public final int minor;
    private final int build;

    public Version(final int major, final int minor, final int build) {
        this.major = major;
        this.minor = minor;
        this.build = build;
    }

    public Version(final String version) {
        final String[] numbers = version.split("[\\.\\-b]");
        major = Integer.parseInt(numbers[0]);
        minor = Integer.parseInt(numbers[1]);
        if (numbers.length > 3) build = Integer.parseInt(numbers[3]);
        else build = 0;
    }

    @Override
    public String toString() {
        if (build > 0) return String.format("%d.%d-b%d", major, minor, build);
        else return String.format("%d.%d", major, minor);
    }

    /**
     * Compare with another version.
     *
     * @param o another version
     * @return 0 if both versions are the same, a negative if the other version is higher and a positive otherwise.
     */
    @Override
    public int compareTo(@SuppressWarnings("NullableProblems") final Version o) {
        if (o == null) return -1;
        if (major - o.major != 0) return major - o.major;
        if (minor - o.minor != 0) return minor - o.minor;

        if (build < 1 || o.build < 1) return 0;
        return build - o.build;
    }

    public boolean compatibleWith(final Version version) {
        return compareTo(version) >= 0;
    }

    /**
     * Check if another version is exactly the same as this one.
     *
     * @param o another version object
     * @return true if both versions are the same, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Version version = (Version) o;
        return major == version.major &&
                minor == version.minor &&
                build == version.build;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, build);
    }
}