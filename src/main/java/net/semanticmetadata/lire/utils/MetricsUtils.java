/*
 * This file is part of the LIRE project: http://lire-project.net
 * LIRE is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * LIRE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LIRE; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * We kindly ask you to refer the any or one of the following publications in
 * any publication mentioning or employing Lire:
 *
 * Lux Mathias, Savvas A. Chatzichristofis. Lire: Lucene Image Retrieval -
 * An Extensible Java CBIR Library. In proceedings of the 16th ACM International
 * Conference on Multimedia, pp. 1085-1088, Vancouver, Canada, 2008
 * URL: http://doi.acm.org/10.1145/1459359.1459577
 *
 * Lux Mathias. Content Based Image Retrieval with LIRE. In proceedings of the
 * 19th ACM International Conference on Multimedia, pp. 735-738, Scottsdale,
 * Arizona, USA, 2011
 * URL: http://dl.acm.org/citation.cfm?id=2072432
 *
 * Mathias Lux, Oge Marques. Visual Information Retrieval using Java and LIRE
 * Morgan & Claypool, 2013
 * URL: http://www.morganclaypool.com/doi/abs/10.2200/S00468ED1V01Y201301ICR025
 *
 * Copyright statement:
 * ====================
 * (c) 2002-2013 by Mathias Lux (mathias@juggle.at)
 *  http://www.semanticmetadata.net/lire, http://www.lire-project.net
 *
 * Updated: 07.08.13 12:18
 */

package net.semanticmetadata.lire.utils;

/**
 * User: mlux
 * Date: 25.11.2009
 * Time: 14:32:49
 */
public class MetricsUtils {
    /**
     * Manhattan distance
     *
     * @param h1
     * @param h2
     * @return
     */
    public static double distL1(int[] h1, int[] h2) {
        assert (h1.length == h2.length);
        double sum = 0d;
        for (int i = 0; i < h1.length; i++) {
            sum += Math.abs(h1[i] - h2[i]);
        }
        return sum / h1.length;
    }

    public static double distL1(short[] h1, short[] h2) {
        assert (h1.length == h2.length);
        double sum = 0d;
        for (int i = 0; i < h1.length; i++) {
            sum += Math.abs(h1[i] - h2[i]);
        }
        return sum / h1.length;
    }

    public static double distL1(double[] h1, double[] h2) {
        assert (h1.length == h2.length);
        double sum = 0d;
        for (int i = 0; i < h1.length; i++) {
            sum += Math.abs(h1[i] - h2[i]);
        }
        return sum / h1.length;
    }

    /**
     * Euclidean distance
     *
     * @param h1
     * @param h2
     * @return
     */
    public static double distL2(int[] h1, int[] h2) {
        assert (h1.length == h2.length);
        double sum = 0d;
        for (int i = 0; i < h1.length; i++) {
            sum += (h1[i] - h2[i]) * (h1[i] - h2[i]);
        }
        return Math.sqrt(sum);
    }

    /**
     * Euclidean distance
     *
     * @param h1
     * @param h2
     * @return
     */
    public static double distL2(double[] h1, double[] h2) {
//        assert (h1.length == h2.length);
        double sum = 0d;
        for (int i = 0; i < h1.length; i++) {
            sum += (h1[i] - h2[i]) * (h1[i] - h2[i]);
        }
        return Math.sqrt(sum);
    }

    /**
     * Euclidean distance
     *
     * @param h1
     * @param h2
     * @return
     */
    public static double distL2(float[] h1, float[] h2) {
        assert (h1.length == h2.length);
        double sum = 0d;
        for (int i = 0; i < h1.length; i++) {
            sum += (h1[i] - h2[i]) * (h1[i] - h2[i]);
        }
        return Math.sqrt(sum);
    }

    /**
     * Jeffrey Divergence or Jensen-Shannon divergence (JSD) from
     * Deselaers, T.; Keysers, D. & Ney, H. Features for image retrieval:
     * an experimental comparison Inf. Retr., Kluwer Academic Publishers, 2008, 11, 77-107
     *
     * @param h1
     * @param h2
     * @return
     */
    public static double jsd(int[] h1, int[] h2) {
        assert (h1.length == h2.length);
        double sum = 0d;
        for (int i = 0; i < h1.length; i++) {
            sum += (h1[i] > 0 ? h1[i] * Math.log(2d * h1[i] / (h1[i] + h2[i])) : 0) +
                    (h2[i] > 0 ? h2[i] * Math.log(2d * h2[i] / (h1[i] + h2[i])) : 0);
        }
        return sum;
    }

    /**
     * Chi^2 statistics.
     *
     * @param d1
     * @param d2
     * @return distance like "unlikelihood"
     */
    public static double chisquare(double[] d1, double[] d2) {
        assert (d1.length == d2.length);
        double sum = 0d;
        double m;
        for (int i = 0; i < d1.length; i++) {
            m = (d1[i] + d2[i]) / 2;
            sum += (d1[i] - m) * (d1[i] - m) / m;
        }
        return sum;
    }

    /**
     * Earth Mover's Distance for two equal length, equal summed histograms as described in
     * Rubner, Yossi, Carlo Tomasi, and Leonidas J. Guibas. "The earth mover's distance as a metric for image
     * retrieval." International journal of computer vision 40.2 (2000): 99-121.
     *
     * @param d1 NOTE: sum(d1) needs to be equal to sum(d2)
     * @param d2
     * @return EMD
     */
    public static double simpleEMD(double[] d1, double[] d2) {
        assert (d1.length == d2.length);
        double sum = 0d;
        double m1 = 0, m2 = 0;
        for (int i = 0; i < d1.length; i++) {
            m1 += d1[i];
            m2 += d2[i];
            sum += Math.abs(m1 - m2);
        }
        return sum;
    }

    /**
     * Kolmogorov-Smirnoff Distance for two equal length, equal summed histograms as described in
     * Rubner, Yossi, Carlo Tomasi, and Leonidas J. Guibas. "The earth mover's distance as a metric for image
     * retrieval." International journal of computer vision 40.2 (2000): 99-121.
     *
     * @param d1 NOTE: sum(d1) needs to be equal to sum(d2)
     * @param d2
     * @return EMD
     */
    public static double ksDistance(double[] d1, double[] d2) {
        assert (d1.length == d2.length);
        double max = 0d;
        double m1 = 0, m2 = 0;
        for (int i = 0; i < d1.length; i++) {
            m1 += d1[i];
            m2 += d2[i];
            max = Math.max(Math.abs(m1 - m2), max);
        }
        return max;
    }

    public static double jsd(byte[] h1, byte[] h2) {
        assert (h1.length == h2.length);
        double sum = 0d;
        for (int i = 0; i < h1.length; i++) {
            sum += (h1[i] > 0 ? h1[i] * Math.log(2d * h1[i] / (h1[i] + h2[i])) : 0) +
                    (h2[i] > 0 ? h2[i] * Math.log(2d * h2[i] / (h1[i] + h2[i])) : 0);
        }
        return sum;
    }

    public static double jsd(float[] h1, float[] h2) {
        assert (h1.length == h2.length);
        double sum = 0d;
        for (int i = 0; i < h1.length; i++) {
            sum += (h1[i] > 0 ? (h1[i] / 2d) * Math.log((2d * h1[i]) / (h1[i] + h2[i])) : 0) +
                    (h2[i] > 0 ? (h2[i] / 2d) * Math.log((2d * h2[i]) / (h1[i] + h2[i])) : 0);
        }
        return sum;
    }

    public static double jsd(double[] h1, double[] h2) {
        assert (h1.length == h2.length);
        double sum = 0d;
        for (int i = 0; i < h1.length; i++) {
            sum += (h1[i] > 0 ? (h1[i] / 2d) * Math.log((2d * h1[i]) / (h1[i] + h2[i])) : 0) +
                    (h2[i] > 0 ? (h2[i] / 2d) * Math.log((2d * h2[i]) / (h1[i] + h2[i])) : 0);
        }
        return sum;
    }

    public static double tanimoto(int[] h1, int[] h2) {
        assert (h1.length == h2.length);
        double result = 0d;
        double tmp1 = 0d;
        double tmp2 = 0d;

        double tmpCnt1 = 0d, tmpCnt2 = 0d, tmpCnt3 = 0d;

        for (int i = 0; i < h1.length; i++) {
            tmp1 += h1[i];
            tmp2 += h2[i];
        }

        if (tmp1 == 0 && tmp2 == 0) return 0;
        if (tmp1 == 0 || tmp2 == 0) return 100;

        if (tmp1 > 0 && tmp2 > 0) {
            for (int i = 0; i < h1.length; i++) {
                tmpCnt1 += (h1[i] / tmp1) * (h2[i] / tmp2);
                tmpCnt2 += (h2[i] / tmp2) * (h2[i] / tmp2);
                tmpCnt3 += (h1[i] / tmp1) * (h1[i] / tmp1);

            }

            result = (100 - 100 * (tmpCnt1 / (tmpCnt2 + tmpCnt3
                    - tmpCnt1))); //Tanimoto
        }
        return result;
    }

    public static double tanimoto(float[] h1, float[] h2) {
        assert (h1.length == h2.length);
        double result = 0d;
        double tmp1 = 0d;
        double tmp2 = 0d;

        double tmpCnt1 = 0, tmpCnt2 = 0, tmpCnt3 = 0;

        for (int i = 0; i < h1.length; i++) {
            tmp1 += h1[i];
            tmp2 += h2[i];
        }

        if (tmp1 == 0 && tmp2 == 0) return 0;
        if (tmp1 == 0 || tmp2 == 0) return 100;

        if (tmp1 > 0 && tmp2 > 0) {
            for (int i = 0; i < h1.length; i++) {
                tmpCnt1 += (h1[i] / tmp1) * (h2[i] / tmp2);
                tmpCnt2 += (h2[i] / tmp2) * (h2[i] / tmp2);
                tmpCnt3 += (h1[i] / tmp1) * (h1[i] / tmp1);

            }

            result = (100 - 100 * (tmpCnt1 / (tmpCnt2 + tmpCnt3
                    - tmpCnt1))); //Tanimoto
        }
        return result;
    }

    public static double tanimoto(double[] h1, double[] h2) {
        assert (h1.length == h2.length);
        double result = 0d;
        double tmp1 = 0d;
        double tmp2 = 0d;

        double tmpCnt1 = 0, tmpCnt2 = 0, tmpCnt3 = 0;

        for (int i = 0; i < h1.length; i++) {
            tmp1 += h1[i];
            tmp2 += h2[i];
        }

        if (tmp1 == 0 && tmp2 == 0) return 0;
        if (tmp1 == 0 || tmp2 == 0) return 100;

        if (tmp1 > 0 && tmp2 > 0) {
            for (int i = 0; i < h1.length; i++) {
                tmpCnt1 += (h1[i] / tmp1) * (h2[i] / tmp2);
                tmpCnt2 += (h2[i] / tmp2) * (h2[i] / tmp2);
                tmpCnt3 += (h1[i] / tmp1) * (h1[i] / tmp1);

            }

            result = (100 - 100 * (tmpCnt1 / (tmpCnt2 + tmpCnt3 - tmpCnt1))); //Tanimoto
        }
        return result;
    }

    public static double cosineCoefficient(double[] hist1, double[] hist2) {
        assert (hist1.length == hist2.length);
        double distance = 0d;
        double tmp1 = 0d, tmp2 = 0d;
        for (int i = 0; i < hist1.length; i++) {
            distance += hist1[i] * hist2[i];
            tmp1 += hist1[i] * hist1[i];
            tmp2 += hist2[i] * hist2[i];
        }
        if (tmp1 * tmp2 > 0) {
            return distance / (Math.sqrt(tmp1) * Math.sqrt(tmp2));
        } else return 1d;
    }

    public static double cosineDistance(double[] hist1, double[] hist2) {
        return 1d-cosineCoefficient(hist1, hist2);
    }

    public static double distL1(float[] h1, float[] h2) {
        assert (h1.length == h2.length);
        double sum = 0d;
        for (int i = 0; i < h1.length; i++) {
            sum += Math.abs(h1[i] - h2[i]);
        }
        return sum;
    }

    public static double distL1(byte[] h1, byte[] h2) {
        assert (h1.length == h2.length);
        double sum = 0d;
        for (int i = 0; i < h1.length; i++) {
            sum += Math.abs(h1[i] - h2[i]);
        }
        return sum;
    }

    /**
     * Max normalization of a double[] histogram.
     *
     * @param histogram
     * @return
     */
    public static double[] normalizeMax(double[] histogram) {
        double[] result = new double[histogram.length];
        double max = Double.MIN_VALUE;
        double min = Double.MAX_VALUE;
        for (int i = 0; i < histogram.length; i++) {
            max = Math.max(max, histogram[i]);
            min = Math.min(min, histogram[i]);
        }
        for (int i = 0; i < histogram.length; i++) {
            result[i] = ((double) histogram[i] - min) / (max - min);
        }
        return result;
    }

    /**
     * Euclidean normalization of a double[] histogram. // todo: make it faster and less memory consuming ...
     *
     * @param histogram
     * @return
     */
    public static double[] normalizeL2(double[] histogram) {
        double[] result = new double[histogram.length];
        double len = 0d;
        for (int i = 0; i < histogram.length; i++) {
            len += histogram[i] * histogram[i];
        }
        len = Math.sqrt(len);
        for (int i = 0; i < histogram.length; i++) {
            if (histogram[i] != 0)
                result[i] = ((double) histogram[i]) / len;
            else
                result[i] = 0;
        }
        return result;
    }

    /**
     * Euclidean normalization of a double[] histogram.  // todo: make it faster and less memory consuming ...
     *
     * @param histogram
     * @return
     */
    public static double[] normalizeL1(double[] histogram) {
        double[] result = new double[histogram.length];
        double len = 0d;
        for (int i = 0; i < histogram.length; i++) {
            len += Math.abs(histogram[i]);
        }
        for (int i = 0; i < histogram.length; i++) {
            result[i] = ((double) histogram[i]) / len;
        }
        return result;
    }


}
