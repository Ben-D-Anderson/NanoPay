package xyz.benanderson.nanopay;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CustomAssertions {

    @SuppressWarnings("SuspiciousMethodCalls")
    public static <T> void assertUnorderedCollectionEquals(Collection<? super T> a, Collection<? super T> b) {
        assertEquals(a.size(), b.size());
        assertTrue(a.containsAll(b));
        assertTrue(b.containsAll(a));
    }

}
