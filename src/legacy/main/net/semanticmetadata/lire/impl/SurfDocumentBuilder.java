/*
 * This file is part of the LIRE project: http://www.semanticmetadata.net/lire
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
 * Updated: 21.04.13 08:59
 */

package net.semanticmetadata.lire.impl;

import com.stromberglabs.jopensurf.SURFInterestPoint;
import com.stromberglabs.jopensurf.Surf;
import net.semanticmetadata.lire.AbstractDocumentBuilder;
import net.semanticmetadata.lire.DocumentBuilder;
import net.semanticmetadata.lire.imageanalysis.SurfFeature;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;

import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.List;

/**
 * User: mathias@juggle.at
 * Date: 29.09.2010
 * Time: 15:41:28
 */
public class SurfDocumentBuilder extends AbstractDocumentBuilder {


    @Override
    public Field[] createDescriptorFields(BufferedImage image) {
        Field[] result = null;
        Surf s = new Surf(image);
        List<SURFInterestPoint> interestPoints = s.getFreeOrientedInterestPoints();
        result = new Field[interestPoints.size()];
        int count = 0;
        for (Iterator<SURFInterestPoint> sipi = interestPoints.iterator(); sipi.hasNext(); ) {
            SURFInterestPoint sip = sipi.next();
            SurfFeature sf = new SurfFeature(sip);
            result[count] = (new StoredField(DocumentBuilder.FIELD_NAME_SURF, sf.getByteArrayRepresentation()));
            count++;
        }
        return result;
    }

    public Document createDocument(BufferedImage image, String identifier) {
        Document doc = null;
        Surf s = new Surf(image);
        List<SURFInterestPoint> interestPoints = s.getFreeOrientedInterestPoints();
        doc = new Document();
        for (Iterator<SURFInterestPoint> sipi = interestPoints.iterator(); sipi.hasNext(); ) {
            SURFInterestPoint sip = sipi.next();
            SurfFeature sf = new SurfFeature(sip);
            doc.add(new StoredField(DocumentBuilder.FIELD_NAME_SURF, sf.getByteArrayRepresentation()));
        }
        if (identifier != null)
            doc.add(new StringField(DocumentBuilder.FIELD_NAME_IDENTIFIER, identifier, Field.Store.YES));
        return doc;
    }
}
