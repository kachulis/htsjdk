/*
* Copyright (c) 2012 The Broad Institute
* 
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
* 
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package htsjdk.variant.variantcontext;


// the imports for unit testing.


import htsjdk.tribble.TribbleException;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.utils.BinomialCoefficientUtil;
import htsjdk.variant.utils.GeneralUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import scala.Int;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;


/**
 * Basic unit test for Genotype likelihoods objects
 */
public class GenotypeLikelihoodsUnitTest extends VariantBaseTest {
    double [] v = new double[]{-10.5, -1.25, -5.11};
    final static String vGLString = "-10.50,-1.25,-5.11";
    final static String vPLString = "93,0,39";
    double[] triAllelic = new double[]{-4.2,-2.0,-3.0,-1.6,0.0,-4.0}; //AA,AB,AC,BB,BC,CC

    @BeforeMethod
    public void initializeAnyploidPLIndexToAlleleIndices() {
        GenotypeLikelihoods.anyploidPloidyToPLIndexToAlleleIndices.clear();
        GenotypeLikelihoods.initializeAnyploidPLIndexToAlleleIndices(1, 1);
        GenotypeLikelihoods.initializeAnyploidPLIndexToAlleleIndices(2, 2);
        GenotypeLikelihoods.initializeAnyploidPLIndexToAlleleIndices(2, 3);
    }

    @Test
    public void testFromVector2() {
        GenotypeLikelihoods gl = GenotypeLikelihoods.fromLog10Likelihoods(v);
        assertDoubleArraysAreEqual(gl.getAsVector(), v);
        Assert.assertEquals(gl.getAsString(), vPLString);
    }

    @Test
    public void testFromString1() {
        GenotypeLikelihoods gl = GenotypeLikelihoods.fromPLField(vPLString);
        assertDoubleArraysAreEqual(gl.getAsVector(), new double[]{-9.3, 0, -3.9});
        Assert.assertEquals(gl.getAsString(), vPLString);
    }

    @Test
    public void testFromString2() {
        GenotypeLikelihoods gl = GenotypeLikelihoods.fromGLField(vGLString);
        assertDoubleArraysAreEqual(gl.getAsVector(), v);
        Assert.assertEquals(gl.getAsString(), vPLString);
    }

    @Test
    public void testFromStringMultipleMissing() {
        String missingWithPloidy2AltAllele1 = ".,.,.";
        GenotypeLikelihoods gl = GenotypeLikelihoods.fromGLField(missingWithPloidy2AltAllele1);
        Assert.assertNull(gl.getAsPLs());
    }

    @Test (expectedExceptions = TribbleException.class)
    public void testFromStringOneMissing() {
        String oneMissingFromPloidy2AltAllele1 = "-3.0,.,-1.2";
        GenotypeLikelihoods.fromGLField(oneMissingFromPloidy2AltAllele1);
    }

    @Test (expectedExceptions = TribbleException.class)
    public void testErrorBadFormat() {
        GenotypeLikelihoods gl = GenotypeLikelihoods.fromPLField("adf,b,c");
        gl.getAsVector();
    }

    @Test
    public void testGetAsMap(){
        GenotypeLikelihoods gl = GenotypeLikelihoods.fromLog10Likelihoods(v);
        //Log scale
        EnumMap<GenotypeType,Double> glMap = gl.getAsMap(false);
        Assert.assertEquals(v[GenotypeType.HOM_REF.ordinal()-1],glMap.get(GenotypeType.HOM_REF));
        Assert.assertEquals(v[GenotypeType.HET.ordinal()-1],glMap.get(GenotypeType.HET));
        Assert.assertEquals(v[GenotypeType.HOM_VAR.ordinal()-1],glMap.get(GenotypeType.HOM_VAR));

        //Linear scale
        glMap = gl.getAsMap(true);
        double [] vl = GeneralUtils.normalizeFromLog10(v);
        Assert.assertEquals(vl[GenotypeType.HOM_REF.ordinal()-1],glMap.get(GenotypeType.HOM_REF));
        Assert.assertEquals(vl[GenotypeType.HET.ordinal()-1],glMap.get(GenotypeType.HET));
        Assert.assertEquals(vl[GenotypeType.HOM_VAR.ordinal()-1],glMap.get(GenotypeType.HOM_VAR));

        //Test missing likelihoods
        gl = GenotypeLikelihoods.fromPLField(".");
        glMap = gl.getAsMap(false);
        Assert.assertNull(glMap);

    }

    @Test
    public void testCalculateNumLikelihoods() {

        for (int nAlleles=2; nAlleles<=5; nAlleles++)
        {
            Assert.assertEquals(GenotypeLikelihoods.numLikelihoods(nAlleles, 2), nAlleles*(nAlleles+1)/2);
        }

        // some special cases: ploidy = 20, #alleles = 4
        Assert.assertEquals(GenotypeLikelihoods.numLikelihoods(4, 20), 1771);
        // ploidy = 10, alleles = 5
        Assert.assertEquals(GenotypeLikelihoods.numLikelihoods(5, 10), 1001);
        // ploidy = 16, alleles = 10
        Assert.assertEquals(GenotypeLikelihoods.numLikelihoods(10, 16), 2042975);
    }

    @DataProvider(name="testNumLikelihoodsCacheDataProvider")
    public Object[][] testNumLikelihoodsCacheDataProvider(){
        return new Object[][]{
                {1, 1, 1},
                {2, 5, 6},
                {10, 16, 2042975}
        };
    }

    @Test(dataProvider="testNumLikelihoodsCacheDataProvider")
    public void testNumLikelihoodsCache(final int numAlleles, final int ploidy, final int numLikelihoods) {
        GenotypeNumLikelihoodsCache cache = new GenotypeNumLikelihoodsCache();

        Assert.assertEquals(cache.get(numAlleles, ploidy), numLikelihoods);
    }

    @DataProvider(name="testNumLikelihoodsCacheIllegalArgumentsDataProvider")
    public Object[][] testNumLikelihoodsCacheIllegalArgumentsDataProvider(){
        return new Object[][]{
            {1, 0},
            {0, 1},
            {-1, 5},
            {3, -4}
        };
    }

    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "testNumLikelihoodsCacheIllegalArgumentsDataProvider")
    public void testNumLikelihoodsCacheIllegalArguments(final int numAlleles, final int ploidy){
        GenotypeNumLikelihoodsCache cache = new GenotypeNumLikelihoodsCache();

        cache.get(numAlleles, ploidy);
    }

    @Test
    public void testGetLog10GQ(){
        GenotypeLikelihoods gl = GenotypeLikelihoods.fromPLField(vPLString);

        //GQ for the best guess genotype
        Assert.assertEquals(gl.getLog10GQ(GenotypeType.HET),-3.9);

        double[] test = GeneralUtils.normalizeFromLog10(gl.getAsVector());

        //GQ for the other genotypes
        Assert.assertEquals(gl.getLog10GQ(GenotypeType.HOM_REF), Math.log10(1.0 - test[GenotypeType.HOM_REF.ordinal()-1]));
        Assert.assertEquals(gl.getLog10GQ(GenotypeType.HOM_VAR), Math.log10(1.0 - test[GenotypeType.HOM_VAR.ordinal()-1]));

       //Test missing likelihoods
        gl = GenotypeLikelihoods.fromPLField(".");
        Assert.assertEquals(gl.getLog10GQ(GenotypeType.HOM_REF),Double.NEGATIVE_INFINITY);
        Assert.assertEquals(gl.getLog10GQ(GenotypeType.HET),Double.NEGATIVE_INFINITY);
        Assert.assertEquals(gl.getLog10GQ(GenotypeType.HOM_VAR),Double.NEGATIVE_INFINITY);

    }

    @Test
    public void testgetQualFromLikelihoods() {
        double[] likelihoods = new double[]{-1, 0, -2};
        // qual values we expect for each possible "best" genotype
        double[] expectedQuals = new double[]{-0.04100161, -1, -0.003930294};

        for ( int i = 0; i < likelihoods.length; i++ ) {
            Assert.assertEquals(GenotypeLikelihoods.getGQLog10FromLikelihoods(i, likelihoods), expectedQuals[i], 1e-6,
                    "GQ value for genotype " + i + " was not calculated correctly");
        }
    }

    // this test is completely broken, the method is wrong.
    public void testGetQualFromLikelihoodsMultiAllelicBroken() {
        GenotypeLikelihoods gl = GenotypeLikelihoods.fromLog10Likelihoods(triAllelic);
        double actualGQ = gl.getLog10GQ(GenotypeType.HET);
        double expectedGQ = 1.6;
        Assert.assertEquals(actualGQ,expectedGQ);
    }

    public void testGetQualFromLikelihoodsMultiAllelic() {
        GenotypeLikelihoods gl = GenotypeLikelihoods.fromLog10Likelihoods(triAllelic);
        Allele ref = Allele.create((byte)'A',true);
        Allele alt1 = Allele.create((byte)'C');
        Allele alt2 = Allele.create((byte)'T');
        List<Allele> allAlleles = Arrays.asList(ref,alt1,alt2);
        List<Allele> gtAlleles = Arrays.asList(alt1,alt2);
        GenotypeBuilder gtBuilder = new GenotypeBuilder();
        gtBuilder.alleles(gtAlleles);
        double actualGQ = gl.getLog10GQ(gtBuilder.make(),allAlleles);
        double expectedGQ = 1.6;
        Assert.assertEquals(actualGQ,expectedGQ);
    }

    private void assertDoubleArraysAreEqual(double[] v1, double[] v2) {
        Assert.assertEquals(v1.length, v2.length);
        for ( int i = 0; i < v1.length; i++ ) {
            Assert.assertEquals(v1[i], v2[i], 1e-6);
        }
    }

    @Test
    public void testCalculatePLindex(){
        int counter = 0;
        for ( int i = 0; i <= 3; i++ ) {
            for ( int j = i; j <= 3; j++ ) {
                Assert.assertEquals(GenotypeLikelihoods.calculatePLindex(i, j), GenotypeLikelihoods.PLindexConversion[counter++], "PL index of alleles " + i + "," + j + " was not calculated correctly");
            }
        }
    }

    @DataProvider
    public Object[][] testGetAllelePairData() {
        return new Object[][]{
                {0, 0, 0},
                {1, 0, 1},
                {2, 1, 1},
                {3, 0, 2},
                {4, 1, 2},
                {5, 2, 2},
                {6, 0, 3},
                {7, 1, 3},
                {8, 2, 3},
                {9, 3, 3}
        };
    }

    @Test(dataProvider = "testGetAllelePairData")
    public void testGetAllelePair(final int PLindex, final int allele1, final int allele2) {
        Assert.assertEquals(GenotypeLikelihoods.getAllelePair(PLindex).alleleIndex1, allele1, "allele index " + allele1 + " from PL index " + PLindex + " was not calculated correctly");
        Assert.assertEquals(GenotypeLikelihoods.getAllelePair(PLindex).alleleIndex2, allele2, "allele index " + allele2 + " from PL index " + PLindex + " was not calculated correctly");
    }

    @DataProvider
    public Object[][] testCalculateAnyploidPLcacheData() {
        return new Object[][]{
                {
                        1, 1,
                        Arrays.asList(Arrays.asList(0),
                                Arrays.asList(1)
                        )
                },
                {
                        2, 2,
                        Arrays.asList(Arrays.asList(0, 0),
                                Arrays.asList(0, 1),
                                Arrays.asList(1, 1),
                                Arrays.asList(0, 2),
                                Arrays.asList(1, 2),
                                Arrays.asList(2, 2)
                        )
                },
                {
                        2, 3,
                        Arrays.asList(Arrays.asList(0, 0, 0),
                                Arrays.asList(0, 0, 1),
                                Arrays.asList(0, 1, 1),
                                Arrays.asList(1, 1, 1),
                                Arrays.asList(0, 0, 2),
                                Arrays.asList(0, 1, 2),
                                Arrays.asList(1, 1, 2),
                                Arrays.asList(0, 2, 2),
                                Arrays.asList(1, 2, 2),
                                Arrays.asList(2, 2, 2)
                        )
                },
                {
                        2, -1,
                        Arrays.asList(Arrays.asList())
                },
                {
                        -1, 2,
                        Arrays.asList(Arrays.asList())
                }
        };
    }

    @Test(dataProvider = "testCalculateAnyploidPLcacheData")
    public void testCalculateAnyploidPLcache(final int altAlleles, final int ploidy, final List<List<Integer>> expected) {
        List<List<Integer>> anyploidPLIndexToAlleleIndices = GenotypeLikelihoods.calculateAnyploidPLcache(altAlleles, ploidy);
        for ( int i=0; i < anyploidPLIndexToAlleleIndices.size(); i++ )
            Assert.assertEquals(anyploidPLIndexToAlleleIndices.get(i), expected.get(i));
    }

    @Test(dataProvider = "testCalculateAnyploidPLcacheData")
    public void testInitializeAnyploidPLIndexToAlleleIndices(final int altAlleles, final int ploidy, final List<List<Integer>> expected) {
        if ( altAlleles >= 1 && ploidy >= 1 ) { // Bypass test with bad data
            Map<Integer, List<List<Integer>>> expectedMap = new HashMap<Integer, List<List<Integer>>>();
            expectedMap.put(ploidy, expected);
            for (Map.Entry<Integer, List<List<Integer>>> entry : GenotypeLikelihoods.anyploidPloidyToPLIndexToAlleleIndices.entrySet()) {
                if (expectedMap.containsKey(entry.getKey()))
                    Assert.assertEquals(entry.getValue(), expectedMap.get(entry.getKey()));
            }
        }
    }

    @DataProvider
    public Object[][] testInitializeAnyploidPLIndexToAlleleIndiceseBadData() {
        return new Object[][]{
                { 2, -1 },
                { -1, 2 }
        };
    }

    @Test(dataProvider = "testInitializeAnyploidPLIndexToAlleleIndiceseBadData", expectedExceptions = IllegalArgumentException.class)
    public void testInitializeAnyploidPLIndexToAlleleIndicesBadData(final int altAlleles, final int ploidy) {
        GenotypeLikelihoods.initializeAnyploidPLIndexToAlleleIndices(altAlleles, ploidy);
    }

    @DataProvider
    public Object[][] testGetAllelesData() {
        return new Object[][]{
                {0, 3, Arrays.asList(0,0,0)},
                {3, 3, Arrays.asList(1,1,1)},
                {1, 3, Arrays.asList(0,0,1)},
                {2, 3, Arrays.asList(0,1,1)},
                {9, 3, Arrays.asList(2,2,2)},
                {6, 3, Arrays.asList(1,1,2)},
                {4, 3, Arrays.asList(0,0,2)},
                {5, 3, Arrays.asList(0,1,2)},
                {7, 3, Arrays.asList(0,2,2)},
                {8, 3, Arrays.asList(1,2,2)},
                {10, 3, Arrays.asList(0,0,3)},
                {14, 3, Arrays.asList(1,2,3)},
                {13, 3, Arrays.asList(0,2,3)},
                {11, 3, Arrays.asList(0,1,3)},
                {12, 3, Arrays.asList(1,1,3)},
                {18, 3, Arrays.asList(2,3,3)},
                {15, 3, Arrays.asList(2,2,3)},
                {16, 3, Arrays.asList(0,3,3)},
                {17, 3, Arrays.asList(1,3,3)},
                {19, 3, Arrays.asList(3,3,3)},
                {1, 1, Collections.singletonList(1)},
                {1539, 5, Arrays.asList(1,3,5,7,9)}, // These tests were derived in reverse, allele arrays we chosen, and index was derived as Index(k_1,k_2,...,k_p) = Sum_m=1^p choose(k_m+m-1, m)
                {1988400, 12, Arrays.asList(0,0,0,0,0,1,3,8,11,11,11,12)},
                {8573,7, Arrays.asList(3,3,5,6,6,8,9)}
        };
    }

    @Test
    public void performanceTest() {
        final Random rand = new Random(1);

        final List<Integer> altAllelesList = new ArrayList<>();
        final List<List<Integer>> plIndexLists = new ArrayList<>();

        final int maxAltAlleles=5;
        final int ploidy=3;
        for (int i = 0; i<1000; i++) {
            final int altAlleles = rand.nextInt(maxAltAlleles - 1) + 2;
            altAllelesList.add(altAlleles);
            plIndexLists.add(new ArrayList<>());
            for (int j =0; j<100000; j++) {
                int maxPLIndex = 0;
                for (int k =1; k<=ploidy; k++) {
                    maxPLIndex+= Math.toIntExact(BinomialCoefficientUtil.binomialCoefficient(altAlleles + k - 1, k));
                }
                plIndexLists.get(i).add(rand.nextInt(maxPLIndex));
            }
        }

        final Instant start = Instant.now();

        for (int i = 0; i < altAllelesList.size(); i++) {
            final List<Integer> plIndexList = plIndexLists.get(i);
            for (int j=0; j< plIndexList.size(); j++) {
                GenotypeLikelihoods.getAlleles(plIndexList.get(j), ploidy);
            }
        }

        final Instant end = Instant.now();

        System.out.println("time to run: " + Duration.between(start, end).toMillis() + " ms");
    }

    @Test(dataProvider = "testGetAllelesData")
    public void testGetAlleles(final int PLindex, final int ploidy, final List<Integer> expected ) {
        Assert.assertEquals(GenotypeLikelihoods.getAlleles(PLindex, ploidy), expected);
    }

    @DataProvider
    public Object[][] testGetAllelesIndexOutOfBoundsData() {
        return new Object[][]{
                {-1, 3},  // PL index too small, non-diploid
                {-1, 2},  // PL index too small, diploid
                {GenotypeLikelihoods.numLikelihoods(GenotypeLikelihoods.MAX_DIPLOID_ALT_ALLELES_THAT_CAN_BE_GENOTYPED+1,2), 2}, // PL index too large, diploid
                {3, -1} // negative ploidy
        };
    }

    @Test(dataProvider = "testGetAllelesIndexOutOfBoundsData", expectedExceptions = IllegalStateException.class)
    public void testGetAllelesOutOfBounds(final int PLindex, final int ploidy) {
        final List<Integer> alleles = GenotypeLikelihoods.getAlleles(PLindex, ploidy);
    }

    @Test
    public void testFromCaseInsensitiveString() {
        GenotypeLikelihoods gl = GenotypeLikelihoods.fromGLField("nan,Infinity,-inf");
        assertDoubleArraysAreEqual(gl.getAsVector(), new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY});
    }
}
