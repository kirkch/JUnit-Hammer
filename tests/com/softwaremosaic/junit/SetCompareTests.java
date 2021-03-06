package com.softwaremosaic.junit;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.softwaremosaic.junit.JUnitMosaic.*;


/**
 *
 */
public class SetCompareTests {

    @SafeVarargs
    private final <T> Set<T> asSet( T... elements ) {
        return new HashSet<T>( Arrays.asList(elements) );
    }

    @Test
    public void compareEmptySetToNullSet_expectEmptyResults() {
        SetComparison<Object> r = compare( asSet(), null );

        assertEquals( 0, r.inBothSets.size() );
        assertEquals( 0, r.onlyInSetA.size() );
        assertEquals( 0, r.onlyInSetB.size() );
    }

    @Test
    public void compareNullToEmptySetSet_expectEmptyResults() {
        SetComparison r = compare( null, asSet() );

        assertEquals( 0, r.inBothSets.size() );
        assertEquals( 0, r.onlyInSetA.size() );
        assertEquals( 0, r.onlyInSetB.size() );
    }

    @Test
    public void compare_1Vnull() {
        SetComparison<Integer> r = compare( asSet(1), null );

        assertEquals( "[1]", r.onlyInSetA.toString() );
        assertEquals( "[]",  r.onlyInSetB.toString() );
        assertEquals( "[]",  r.inBothSets.toString() );
    }

    @Test
    public void compare_1Vempty() {
        SetComparison<Integer> r = compare( asSet(1), new HashSet<Integer>() );

        assertEquals( "[1]", r.onlyInSetA.toString() );
        assertEquals( "[]",  r.onlyInSetB.toString() );
        assertEquals( "[]",  r.inBothSets.toString() );
    }

    @Test
    public void compare_1V1() {
        SetComparison<Integer> r = compare( asSet(1), asSet(1) );

        assertEquals( "[]",  r.onlyInSetA.toString() );
        assertEquals( "[]",  r.onlyInSetB.toString() );
        assertEquals( "[1]", r.inBothSets.toString() );
    }

    @Test
    public void compare_1V0() {
        SetComparison<Integer> r = compare( asSet(1), asSet(0) );

        assertEquals( "[1]",  r.onlyInSetA.toString() );
        assertEquals( "[0]",  r.onlyInSetB.toString() );
        assertEquals( "[]", r.inBothSets.toString() );
    }

    @Test
    public void compare_12V1() {
        SetComparison<Integer> r = compare( asSet(1,2), asSet(1) );

        assertEquals( "[2]",  r.onlyInSetA.toString() );
        assertEquals( "[]",  r.onlyInSetB.toString() );
        assertEquals( "[1]", r.inBothSets.toString() );
    }

    @Test
    public void compare_1V12() {
        SetComparison<Integer> r = compare( asSet(1), asSet(1,2) );

        assertEquals( "[]",  r.onlyInSetA.toString() );
        assertEquals( "[2]",  r.onlyInSetB.toString() );
        assertEquals( "[1]", r.inBothSets.toString() );
    }

}

