package android.net;
/** Host-test stand-in; production always uses the Android framework implementation. */
public final class Uri {
    private final String value;
    private Uri(String value) { this.value = value; }
    public static Uri parse(String value) { return new Uri(value); }
    @Override public String toString() { return value; }
    @Override public boolean equals(Object other) { return other instanceof Uri && value.equals(((Uri)other).value); }
    @Override public int hashCode() { return value.hashCode(); }
}
