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
 * Updated: 11.07.13 10:42
 */

package net.semanticmetadata.lire.imageanalysis.features.local.surf;

import com.stromberglabs.jopensurf.SURFInterestPoint;
import net.semanticmetadata.lire.builders.DocumentBuilder;
import net.semanticmetadata.lire.imageanalysis.features.LireFeature;
import net.semanticmetadata.lire.imageanalysis.features.LocalFeature;
import net.semanticmetadata.lire.imageanalysis.features.global.CEDD;
import net.semanticmetadata.lire.utils.MetricsUtils;
import net.semanticmetadata.lire.utils.SerializationUtils;

/**
 * Mathias Lux, mathias@juggle.at
 * Date: 29.09.2010
 * Time: 15:44:14
 */
public class SurfFeature implements LocalFeature {
    private double X = -1, Y = -1;
    private double size = -1;
    private double[] feature = null;

    public SurfFeature(SURFInterestPoint surfInterestPoint) {
        this.X = surfInterestPoint.getX();
        this.Y = surfInterestPoint.getY();
        this.size = surfInterestPoint.getScale(); //TODO: size!!!
        feature = SerializationUtils.toDoubleArray(surfInterestPoint.getDescriptor());
    }

    public SurfFeature() { }

    @Override
    public double getDistance(LireFeature f) {
        if (!(f instanceof SurfFeature)) return -1;
        return MetricsUtils.distL2(feature, ((SurfFeature) f).feature);
    }

//    public String getStringRepresentation() {
//        throw new UnsupportedOperationException("Not implemented!");
//    }
//
//    public void setStringRepresentation(String s) {
//        double[] result = null;
//        LinkedList<Float> tmp = new LinkedList<Float>();
//        StringTokenizer st = new StringTokenizer(s);
//        st.nextToken(); // point.x
//        st.nextToken(); // point.y
//        st.nextToken(); // response
//        while (st.hasMoreTokens())
//            tmp.add(Float.parseFloat(st.nextToken()));
//        result = new double[tmp.size()];
//        int i = 0;
//        for (Iterator<Float> iterator = tmp.iterator(); iterator.hasNext(); ) {
//            Float next = iterator.next();
//            result[i] = next;
//            i++;
//        }
//        descriptor = result;
//
//    }

    /**
     * Provides a much faster way of serialization.
     *
     * @return a byte array that can be read with the corresponding method.
     * @see CEDD#setByteArrayRepresentation(byte[])
     */
    @Override
    public byte[] getByteArrayRepresentation() {
        return SerializationUtils.toByteArray(feature);
    }

    /**
     * Reads descriptor from a byte array. Much faster than the String based method.
     *
     * @param in byte array from corresponding method
     * @see CEDD#getByteArrayRepresentation
     */
    @Override
    public void setByteArrayRepresentation(byte[] in) {
        feature = SerializationUtils.toDoubleArray(in);
    }

    @Override
    public void setByteArrayRepresentation(byte[] in, int offset, int length) {
        feature = SerializationUtils.toDoubleArray(in, offset, length);
    }

    @Override
    public double[] getFeatureVector() {
        return feature;
    }

    @Override
    public String getFeatureName() {
        return "SURF";
    }

    @Override
    public String getFieldName() {
        return DocumentBuilder.FIELD_NAME_SURF;
    }

    @Override
    public double getX() {
        return X;
    }

    @Override
    public double getY() {
        return Y;
    }

    @Override
    public double getSize() {
        return size;
    }

    @Override
    public Class<?> getClassOfExtractor() {
        return SurfExtractor.class;
    }
}
