package com.mosaic.junitpro;



import net.java.quickcheck.Generator;
import org.junit.internal.AssumptionViolatedException;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
@SuppressWarnings("unchecked")
public class JUnitExt extends BlockJUnit4ClassRunner {

    static {
        MemChecker.startMemCheckRegion(false);  // dummy call to bootstrap the MemChecker class before it is needed
    }


    private List<FrameworkMethod> list = null;

    public JUnitExt(Class<?> klass) throws InitializationError {
        super(klass);
    }

    @Override
    protected List<FrameworkMethod> computeTestMethods() {
        if ( list == null ) {
            List<FrameworkMethod> annotatedMethods1 = getTestClass().getAnnotatedMethods(Test.class);
            List<FrameworkMethod> annotatedMethods2 = getTestClass().getAnnotatedMethods(org.junit.Test.class);
            List<FrameworkMethod> annotatedMethods3 = getTestClass().getAnnotatedMethods(Benchmark.class);

            list = new ArrayList(annotatedMethods1.size()+annotatedMethods2.size()+annotatedMethods3.size());
            list.addAll(annotatedMethods1);
            list.addAll(annotatedMethods2);
            list.addAll(annotatedMethods3);
        }

        return list;
    }

    @Override
    protected void validateTestMethods(List<Throwable> errors) {}

    @Override
    protected Statement methodInvoker(FrameworkMethod method, Object test) {
        Benchmark benchmarkAnnotation = method.getAnnotation(Benchmark.class);

        if ( benchmarkAnnotation == null ) {
            Test testAnnotation = method.getAnnotation(Test.class);

            return new InvokeTestMethod( method, test, testAnnotation );
        } else {
            return new InvokeBenchmarkMethod( method, test, benchmarkAnnotation );
        }
    }

}


@SuppressWarnings("unchecked")
class InvokeTestMethod extends Statement {

    private final FrameworkMethod fTestMethod;
    private final Object          fTarget;
    private final Test            testAnnotation;



    public InvokeTestMethod( FrameworkMethod testMethod, Object target, Test testAnnotation ) {
        fTestMethod         = testMethod;
        fTarget             = target;
        this.testAnnotation = testAnnotation;
    }

    @Override
    public void evaluate() throws Throwable {
        verifyParameters();

        Generator[] generators        = fetchGenerators();
        int         numRuns           = calculateNumberOfRuns(generators);
        boolean     isMemCheckEnabled = isMemCheckEnabled();

        // TODO default generator values

        TestExecutionLock.acquireTestLock();

        try {
            for ( int i=0; i<numRuns; i++ ) {
                MemChecker.startMemCheckRegion( isMemCheckEnabled );

                Object[] paramValues = generateParameterValues( generators );

                if ( isMemCheckEnabled && paramValues.length == 0 ) {
                    throw new UnsupportedOperationException("@Test(memCheck=true) requires a test method that takes parameters");
                }

                MemChecker.watchValue( paramValues );

                try {
                    fTestMethod.invokeExplosively(fTarget, paramValues);
                } catch (AssumptionViolatedException e) {
                    // ignore failed assumptions
                } finally {
                    //noinspection UnusedAssignment
                    paramValues = null;  // makes the param values GC'able

                    MemChecker.endMemCheckRegion();
                }
            }
        } finally {
            TestExecutionLock.releaseTestLock();
        }
    }

    private int calculateNumberOfRuns(Generator[] generators) {
        if ( generators.length > 0 ) {
            return testAnnotation.repeat();
        } else if ( testAnnotation != null && testAnnotation.memCheck() ) {
            return 2; // we run twice so that the first run can prime the system and avoid jvm caches
                      // from showing up as leaks; the second run is the real run with memcheck testing
        }

        return 1;
    }

    private boolean isMemCheckEnabled() {
        return testAnnotation != null && testAnnotation.memCheck();
    }

    private void verifyParameters() {
        int numParams     = fTestMethod.getMethod().getParameterTypes().length;
        int numGenerators = testAnnotation == null ? 0 : testAnnotation.generators().length;

        if ( numGenerators != numParams ) {
            String msg = String.format("Test method has %s parameters declared, and %s generators.  There should be exactly one generator per parameter.", numParams, numGenerators);

            throw new IllegalArgumentException(msg);
        }
    }

    private Generator[] fetchGenerators() {
        if ( testAnnotation == null )  {
            return new Generator[] {};
        }

        String[]    generatorFieldNames = testAnnotation.generators();
        int         numGenerators       = generatorFieldNames.length;
        Generator[] generators          = new Generator[numGenerators];

        for ( int i=0; i<numGenerators; i++ ) {
            String fieldName = generatorFieldNames[i];

            try {
                Field field = locateMandatoryField(fieldName);
                field.setAccessible(true);

                generators[i] = (Generator) field.get(fTarget);
            } catch (Exception e) {
                throw new IllegalArgumentException( "Unable to retrieve generator from field '"+fieldName+"'", e );
            }
        }

        return generators;
    }

    private Object[] generateParameterValues(Generator[] generators) {
        int      numGenerators = generators.length;
        Object[] values        = new Object[numGenerators];

        for ( int i=0; i<numGenerators; i++ ) {
            values[i] = generators[i].next();
        }

        return values;
    }

    private Field locateMandatoryField( String fieldName ) {
        for ( Class c : getSuperClasses() ) {
            try {
                return c.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                // carry on looking
            }
        }

        throw new IllegalArgumentException( "Unable to locate generator in field '"+fieldName+"'");
    }

    private List<Class<?>> getSuperClasses() {
        ArrayList results = new ArrayList();
        Class     current = fTarget.getClass();

        while (current != null) {
            results.add(current);
            current = current.getSuperclass();
        }

        return results;
    }
}


@SuppressWarnings("unchecked")
class InvokeBenchmarkMethod extends Statement {

    private final FrameworkMethod fTestMethod;
    private final Object          fTarget;
    private final Benchmark       annotation;

    public InvokeBenchmarkMethod( FrameworkMethod testMethod, Object target, Benchmark annotation ) {
        fTestMethod     = testMethod;
        fTarget         = target;
        this.annotation = annotation;
    }

    @Override
    public void evaluate() throws Throwable {
//        verifyParameters();

        TestExecutionLock.acquireBenchmarkLock();

        System.out.println( "Benchmark results for: " );
        System.out.println( "Invoking "+fTestMethod.getMethod().getDeclaringClass().getSimpleName()+"."+fTestMethod.getName() + " (batchCount=" + annotation.batchCount() + ", iterationCount=" + annotation.value() + ", timingMultipler=" + annotation.durationResultMultiplier() + ")" );
        System.out.println( "    " );

        try {
            for ( int i=0; i<annotation.batchCount()+1; i++ ) {
                invokeAndReportBatch(i);
            }
        } finally {
            TestExecutionLock.releaseBenchmarkLock();
        }
    }

    private void invokeAndReportBatch( int runCount ) throws Throwable {
        long   durationNanosRaw = invokeBatch();
        double durationNanos    = durationNanosRaw * annotation.durationResultMultiplier();

        double perCallNanos  = durationNanos / annotation.value();
        double perCallMillis = perCallNanos  / 1000000.0;

        if ( runCount > 0 ) {
            System.out.print( "    " );

            if ( perCallMillis < 1 ) {
                System.out.print( String.format("%.2f",perCallNanos)  );
                System.out.println( "ns per " + annotation.units() );
            } else {
                System.out.print( String.format("%.2f",perCallMillis)  );
                System.out.print( "ms per " + annotation.units() );
            }
        }
    }

    private long invokeBatch() throws Throwable {
        System.gc();

        int    numIterations = annotation.value();
        Method method        = fTestMethod.getMethod();

        long startNanos = System.nanoTime();
        if ( method.getParameterTypes().length == 1 ) {
            method.invoke(fTarget, annotation.value());
        } else {
            for ( int i=0; i<numIterations; i++ ) {
                method.invoke(fTarget);
            }
        }

        return System.nanoTime() - startNanos;
    }

//    private void verifyParameters() {
//        int numParams     = fTestMethod.getMethod().getParameterTypes().length;
//        int numGenerators = testAnnotation == null ? 0 : testAnnotation.generators().length;
//
//        if ( numGenerators != numParams ) {
//            String msg = String.format("Test method has %s parameters declared, and %s generators.  There should be exactly one generator per parameter.", numParams, numGenerators);
//
//            throw new IllegalArgumentException(msg);
//        }
//    }

}