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
 */

package net.semanticmetadata.lire.indexers.parallel;

import net.semanticmetadata.lire.aggregators.AbstractAggregator;
import net.semanticmetadata.lire.aggregators.BOVW;
import net.semanticmetadata.lire.builders.*;
import net.semanticmetadata.lire.classifiers.Cluster;
import net.semanticmetadata.lire.classifiers.KMeans;
import net.semanticmetadata.lire.classifiers.ParallelKMeans;
import net.semanticmetadata.lire.imageanalysis.features.Extractor;
import net.semanticmetadata.lire.imageanalysis.features.GlobalFeature;
import net.semanticmetadata.lire.imageanalysis.features.LocalFeature;
import net.semanticmetadata.lire.imageanalysis.features.LocalFeatureExtractor;
import net.semanticmetadata.lire.imageanalysis.features.global.CEDD;
import net.semanticmetadata.lire.imageanalysis.features.global.FCTH;
import net.semanticmetadata.lire.imageanalysis.features.global.JCD;
import net.semanticmetadata.lire.imageanalysis.features.local.simple.SimpleExtractor;
import net.semanticmetadata.lire.utils.FileUtils;
import net.semanticmetadata.lire.utils.LuceneUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;
import java.nio.file.*;

//import net.semanticmetadata.lire.imageanalysis.features.global.ACCID;

/**
 * This class allows for creating indexes in a parallel manner. The class
 * at hand reads files from the disk and acts as producer, while several consumer
 * threads extract the features from the given files.
 * <p/>
 * Use the methods {@link ParallelCsvIndexer#addExtractor} to add your own features.
 * Check the source of this class -- the main method -- to get an idea.
 * <p/>
 * Created by mlux on 15/04/2013.
 *
 * @author Mathias Lux, mathias@juggle.at
 * @author Nektarios Anagnostopoulos, nek.anag@gmail.com
 */
public class ParallelCsvIndexer implements Runnable {
    private boolean useDocValues = false;
    private Logger log = Logger.getLogger(this.getClass().getName());
    private ProgressMonitor pm = null;
    private DecimalFormat df = (DecimalFormat) NumberFormat.getNumberInstance();
    private int numOfThreads = DocumentBuilder.NUM_OF_THREADS;
    private int monitoringInterval = 1; // all xx seconds a status message will be displayed
    private int overallCount = -1, numImages = -1; //, numSample = -1
    private boolean overWrite = true;   //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    private boolean useParallelClustering = true;
    private boolean indexingFinished = false;
    private boolean lockLists = false;
    private boolean sampling = false;
    private boolean appending = false;
    private boolean globalHashing = true;
    private GlobalDocumentBuilder.HashingMode globalHashingMode = GlobalDocumentBuilder.HashingMode.BitSampling;

    private IndexWriter writer;
    private String imageDirectory, indexPath, csvFilePath;
    private OutputStream dos = null;
    private File imageList = null;
    private List<String> allImages, sampleImages;

    private int numOfDocsForCodebooks = 300;
    private int[] numOfClusters = new int[]{512};
    private TreeSet<Integer> numOfClustersSet = new TreeSet<Integer>();

    private HashSet<ExtractorItem> GlobalExtractors = new HashSet<ExtractorItem>(10); // default size (16)
    private HashMap<ExtractorItem, LinkedList<Cluster[]>> LocalExtractorsAndCodebooks = new HashMap<ExtractorItem, LinkedList<Cluster[]>>(10); // default size (16)
    private HashMap<ExtractorItem, LinkedList<Cluster[]>> SimpleExtractorsAndCodebooks = new HashMap<ExtractorItem, LinkedList<Cluster[]>>(10); // default size (16)

    private Class<? extends DocumentBuilder> customDocumentBuilder = null;
    private boolean customDocBuilderFlag = false;

    private ConcurrentHashMap<String, List<? extends LocalFeature>> conSampleMap;

    private Class<? extends AbstractAggregator> aggregator = BOVW.class;

    private HashMap<String, Document> allDocuments;

    private ImagePreprocessor imagePreprocessor;

    // Note that you can edit the queue size here. 100 is a good value, but I'd raise it to 200.
    private int queueCapacity = 200;
    private LinkedBlockingQueue<WorkItem> queue = new LinkedBlockingQueue<>(queueCapacity);


    public static void main(String[] args) {
        String indexPath = null;
        String imageDirectory = null;
        File imageList = null;
        int numThreads = 10;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("-i")) {  // index
                if ((i + 1) < args.length) {
                    indexPath = args[i + 1];
                }
            } else if (arg.startsWith("-n")) { // number of Threads
                if ((i + 1) < args.length) {
                    try {
                        numThreads = Integer.parseInt(args[i + 1]);
                    } catch (NumberFormatException e) {
                        System.err.println("Could not read number of threads: " + args[i + 1] + "\nUsing default value " + numThreads);
                    }
                }
            } else if (arg.startsWith("-l")) { // list of images in a file ...
                imageDirectory = null;
                if ((i + 1) < args.length) {
                    imageList = new File(args[i + 1]);
                    if (!imageList.exists()) {
                        System.err.println(args[i + 1] + " does not exits!");
                        printHelp();
                        System.exit(-1);
                    }
                }
            } else if (arg.startsWith("-d")) { // image directory
                if ((i + 1) < args.length) {
                    imageDirectory = args[i + 1];
                }
            }
        }

        if (indexPath == null) {
            printHelp();
            System.exit(-1);
        } else if (imageList == null && (imageDirectory == null || !new File(imageDirectory).exists())) {
            printHelp();
            System.exit(-1);
        }
        ParallelCsvIndexer p;
        if (imageList != null) {
            p = new ParallelCsvIndexer(numThreads, indexPath, imageList);
        } else {
            p = new ParallelCsvIndexer(numThreads, indexPath, imageDirectory);
        }
//        p.addExtractor(ACCID.class);
        p.addExtractor(CEDD.class);
        p.addExtractor(FCTH.class);
        p.addExtractor(JCD.class);
        p.run();
    }

    /**
     * Prints help text in case the thing is not configured correctly.
     */
    private static void printHelp() {
        System.out.println("Usage:\n" +
                "\n" +
                "$> ParallelCsvIndexer -i <index> <-d <image-directory> | -l <image-list>> [-n <number of threads>]\n" +
                "\n" +
                "index             ... The directory of the index. Will be appended or created if not existing.\n" +
                "images-directory  ... The directory the images are found in. It's traversed recursively.\n" +
                "image-list        ... A list of images in a file, one per line. Use instead of images-directory.\n" +
                "number of threads ... The number of threads used for extracting features, e.g. # of CPU cores.");
    }


    public ParallelCsvIndexer(int numOfThreads, String indexPath, String imageDirectory, String csvFilePath) {
        this.numOfThreads = numOfThreads;
        this.indexPath = indexPath;
        this.imageDirectory = imageDirectory;
        this.csvFilePath = csvFilePath;
    }

    public ParallelCsvIndexer(int numOfThreads, String indexPath, String imageDirectory) {
        this.numOfThreads = numOfThreads;
        this.indexPath = indexPath;
        this.imageDirectory = imageDirectory;
    }

    public ParallelCsvIndexer(int numOfThreads, String indexPath, String imageDirectory, int numOfClusters, int numOfDocsForCodebooks) {
        this.numOfThreads = numOfThreads;
        this.indexPath = indexPath;
        this.imageDirectory = imageDirectory;
        this.numOfClusters = new int[]{numOfClusters};
        this.numOfDocsForCodebooks = numOfDocsForCodebooks;
    }

    public ParallelCsvIndexer(int numOfThreads, String indexPath, String imageDirectory, int numOfClusters, int numOfDocsForCodebooks, Class<? extends AbstractAggregator> aggregator) {
        this.numOfThreads = numOfThreads;
        this.indexPath = indexPath;
        this.imageDirectory = imageDirectory;
        this.numOfClusters = new int[]{numOfClusters};
        this.numOfDocsForCodebooks = numOfDocsForCodebooks;
        this.aggregator = aggregator;
    }

    public ParallelCsvIndexer(int numOfThreads, String indexPath, String imageDirectory, int[] numOfClusters, int numOfDocsForCodebooks) {
        this.numOfThreads = numOfThreads;
        this.indexPath = indexPath;
        this.imageDirectory = imageDirectory;
        this.numOfClusters = numOfClusters;
        this.numOfDocsForCodebooks = numOfDocsForCodebooks;
    }

    public ParallelCsvIndexer(int numOfThreads, String indexPath, String imageDirectory, int[] numOfClusters, int numOfDocsForCodebooks, Class<? extends AbstractAggregator> aggregator) {
        this.numOfThreads = numOfThreads;
        this.indexPath = indexPath;
        this.imageDirectory = imageDirectory;
        this.numOfClusters = numOfClusters;
        this.numOfDocsForCodebooks = numOfDocsForCodebooks;
        this.aggregator = aggregator;
    }

    //imageList
    public ParallelCsvIndexer(int numOfThreads, String indexPath, File imageList) {
        this.numOfThreads = numOfThreads;
        this.indexPath = indexPath;
        this.imageList = imageList;
    }

    /**
     * Constructor for use with hashing.
     *
     * @param numOfThreads number of threads used for processing.
     * @param indexPath    the directory the index witll be written to.
     * @param imageList    the list of images, one path per line.
     * @param hashingMode  the mode used for Hashing, use HashingMode.None if you don't want hashing.
     */
    public ParallelCsvIndexer(int numOfThreads, String indexPath, File imageList, GlobalDocumentBuilder.HashingMode hashingMode) {
        this.numOfThreads = numOfThreads;
        this.indexPath = indexPath;
        this.imageList = imageList;
        if (hashingMode != GlobalDocumentBuilder.HashingMode.None) {
            this.globalHashing = true;
        } else this.globalHashing = false;
        this.globalHashingMode = hashingMode;
    }

    /**
     * Constructor for use with hashing and optional storage in DocValues instead of Lucene fields.
     *
     * @param numOfThreads number of threads used for processing.
     * @param indexPath    the directory the index witll be written to.
     * @param imageList    the list of images, one path per line.
     * @param hashingMode  the mode used for Hashing, use HashingMode.None if you don't want hashing.
     * @param useDocValues set to true if you want to use DocValues instead of Fields.
     */
    public ParallelCsvIndexer(int numOfThreads, String indexPath, File imageList, GlobalDocumentBuilder.HashingMode hashingMode, boolean useDocValues) {
        this.numOfThreads = numOfThreads;
        this.indexPath = indexPath;
        this.imageList = imageList;
        if (hashingMode != GlobalDocumentBuilder.HashingMode.None) {
            this.globalHashing = true;
        } else {
            this.globalHashing = false;
        }
        this.globalHashingMode = hashingMode;
        this.useDocValues = useDocValues;
    }

    /**
     * Constructor for use with hashing and optional storage in DocValues instead of Lucene fields.
     *
     * @param numOfThreads number of threads used for processing.
     * @param indexPath    the directory the index witll be written to.
     * @param imageList    the list of images, one path per line.
     * @param hashingMode  the mode used for Hashing, use HashingMode.None if you don't want hashing.
     * @param useDocValues set to true if you want to use DocValues instead of Fields.
     * @param queueSize    the size of the reading queue to minimize disk usage.
     */
    public ParallelCsvIndexer(int numOfThreads, String indexPath, File imageList, GlobalDocumentBuilder.HashingMode hashingMode, boolean useDocValues, int queueSize) {
        this.numOfThreads = numOfThreads;
        this.indexPath = indexPath;
        this.imageList = imageList;
        if (hashingMode != GlobalDocumentBuilder.HashingMode.None) {
            this.globalHashing = true;
        } else {
            this.globalHashing = false;
        }
        this.globalHashingMode = hashingMode;
        this.useDocValues = useDocValues;
        queueCapacity = queueSize;
        queue = new LinkedBlockingQueue<>(queueSize);
    }

    public ParallelCsvIndexer(int numOfThreads, String indexPath, File imageList, int numOfClusters, int numOfDocsForCodebooks) {
        this.numOfThreads = numOfThreads;
        this.indexPath = indexPath;
        this.imageList = imageList;
        this.numOfClusters = new int[]{numOfClusters};
        this.numOfDocsForCodebooks = numOfDocsForCodebooks;
    }

    public ParallelCsvIndexer(int numOfThreads, String indexPath, File imageList, int numOfClusters, int numOfDocsForCodebooks, Class<? extends AbstractAggregator> aggregator) {
        this.numOfThreads = numOfThreads;
        this.indexPath = indexPath;
        this.imageList = imageList;
        this.numOfClusters = new int[]{numOfClusters};
        this.numOfDocsForCodebooks = numOfDocsForCodebooks;
        this.aggregator = aggregator;
    }

    public ParallelCsvIndexer(int numOfThreads, String indexPath, File imageList, int[] numOfClusters, int numOfDocsForCodebooks) {
        this.numOfThreads = numOfThreads;
        this.indexPath = indexPath;
        this.imageList = imageList;
        this.numOfClusters = numOfClusters;
        this.numOfDocsForCodebooks = numOfDocsForCodebooks;
    }

    public ParallelCsvIndexer(int numOfThreads, String indexPath, File imageList, int[] numOfClusters, int numOfDocsForCodebooks, Class<? extends AbstractAggregator> aggregator) {
        this.numOfThreads = numOfThreads;
        this.indexPath = indexPath;
        this.imageList = imageList;
        this.numOfClusters = numOfClusters;
        this.numOfDocsForCodebooks = numOfDocsForCodebooks;
        this.aggregator = aggregator;
    }

    public void addExtractor(Class<? extends Extractor> extractorClass) {
        if (lockLists) throw new UnsupportedOperationException("Cannot add extractors!");
        ExtractorItem extractorItem = new ExtractorItem(extractorClass);
        boolean flag = true;
        if (extractorItem.isGlobal()) {
            for (ExtractorItem next : GlobalExtractors) {
                if (next.getExtractorClass().equals(extractorClass)) {
                    flag = false;
                }
            }
            if (flag) {
                this.GlobalExtractors.add(extractorItem);
            } else {
                throw new UnsupportedOperationException(extractorClass.getSimpleName() + " already exists!!");
            }
        } else if (extractorItem.isLocal()) {
            for (Map.Entry<ExtractorItem, LinkedList<Cluster[]>> next : LocalExtractorsAndCodebooks.entrySet()) {
                if (next.getKey().getExtractorClass().equals(extractorClass)) {
                    flag = false;
                }
            }
            if (flag) {
                this.LocalExtractorsAndCodebooks.put(extractorItem, new LinkedList<Cluster[]>());
                this.sampling = true;
            } else {
                throw new UnsupportedOperationException(extractorClass.getSimpleName() + " already exists!!");
            }
        } else throw new UnsupportedOperationException("Error");
    }

    public void addExtractor(Class<? extends GlobalFeature> globalFeatureClass, SimpleExtractor.KeypointDetector detector) {
        if (lockLists) throw new UnsupportedOperationException("Cannot add extractors!");
        boolean flag = true;
        for (Map.Entry<ExtractorItem, LinkedList<Cluster[]>> next : SimpleExtractorsAndCodebooks.entrySet()) {
            if ((next.getKey().getExtractorClass().equals(globalFeatureClass)) && (next.getKey().getKeypointDetector() == detector)) {
                flag = false;
            }
        }
        if (flag) {
            this.SimpleExtractorsAndCodebooks.put(new ExtractorItem(globalFeatureClass, detector), new LinkedList<Cluster[]>());
            this.sampling = true;
        } else {
            throw new UnsupportedOperationException(globalFeatureClass.getSimpleName() + " with " + detector.name() + " already exists!!");
        }
    }

    public void addExtractor(Class<? extends GlobalFeature> globalFeatureClass, SimpleExtractor.KeypointDetector detector, int numKeyPoints) {
        if (lockLists) throw new UnsupportedOperationException("Cannot add extractors!");
        boolean flag = true;
        for (Map.Entry<ExtractorItem, LinkedList<Cluster[]>> next : SimpleExtractorsAndCodebooks.entrySet()) {
            if ((next.getKey().getExtractorClass().equals(globalFeatureClass)) && (next.getKey().getKeypointDetector() == detector)) {
                flag = false;
            }
        }
        if (flag) {
            this.SimpleExtractorsAndCodebooks.put(new ExtractorItem(globalFeatureClass, detector, numKeyPoints), new LinkedList<Cluster[]>());
            this.sampling = true;
        } else {
            throw new UnsupportedOperationException(globalFeatureClass.getSimpleName() + " with " + detector.name() + " already exists!!");
        }
    }

    public void addExtractor(Class<? extends LocalFeatureExtractor> localFeatureExtractorClass, Cluster[] codebook) {
        LinkedList<Cluster[]> tmpList = new LinkedList<Cluster[]>();
        tmpList.add(codebook);
        addExtractor(localFeatureExtractorClass, tmpList);
    }

    public void addExtractor(Class<? extends GlobalFeature> globalFeatureClass, SimpleExtractor.KeypointDetector detector, Cluster[] codebook) {
        LinkedList<Cluster[]> tmpList = new LinkedList<Cluster[]>();
        tmpList.add(codebook);
        addExtractor(globalFeatureClass, detector, tmpList);
    }

    public void addExtractor(Class<? extends LocalFeatureExtractor> localFeatureExtractorClass, LinkedList<Cluster[]> codebooks) {
        if (lockLists) throw new UnsupportedOperationException("Cannot add extractors!");
        (new File(indexPath + ".config/")).mkdirs();
        ExtractorItem extractorItem = new ExtractorItem(localFeatureExtractorClass);
        boolean found, flag = true;
        for (Map.Entry<ExtractorItem, LinkedList<Cluster[]>> next : LocalExtractorsAndCodebooks.entrySet()) {
            if (next.getKey().getExtractorClass().equals(localFeatureExtractorClass)) {
                flag = false;
            }
        }
        if (flag) {
            boolean flagForSize;
            for (Cluster[] codebook : codebooks) {
                flagForSize = false;
                for (int next : numOfClusters) {
                    if (codebook.length == next) flagForSize = true;
                }
                if (!flagForSize) {
                    System.err.println("Codebook of " + codebook.length + " clusters will be removed as such number of clusters is not selected!!");
                    codebooks.remove(codebook);
                }
            }
            if (!appending) {
                for (Cluster[] codebook : codebooks) {
                    try {
                        Cluster.writeClusters(codebook, indexPath + ".config/" + (extractorItem.getFeatureInstance()).getFieldName() + codebook.length);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            this.LocalExtractorsAndCodebooks.put(extractorItem, codebooks);

            for (int numOfCluster : numOfClusters) {
                found = false;
                for (Cluster[] codebook : codebooks) {
                    if (codebook.length == numOfCluster) {
                        found = true;
                    }
                }
                if (!found) this.sampling = true;
            }
        } else {
            throw new UnsupportedOperationException(localFeatureExtractorClass.getSimpleName() + " already exists!!");
        }
    }

    public void addExtractor(Class<? extends GlobalFeature> globalFeatureClass, SimpleExtractor.KeypointDetector detector, LinkedList<Cluster[]> codebooks) {
        if (lockLists) throw new UnsupportedOperationException("Cannot add extractors!");
        (new File(indexPath + ".config/")).mkdirs();
        ExtractorItem extractorItem = new ExtractorItem(globalFeatureClass, detector);
        boolean found, flag = true;
        for (Map.Entry<ExtractorItem, LinkedList<Cluster[]>> next : SimpleExtractorsAndCodebooks.entrySet()) {
            if ((next.getKey().getExtractorClass().equals(globalFeatureClass)) && (next.getKey().getKeypointDetector() == detector)) {
                flag = false;
            }
        }
        if (flag) {
            boolean flagForSize;
            for (Cluster[] codebook : codebooks) {
                flagForSize = false;
                for (int next : numOfClusters) {
                    if (codebook.length == next) flagForSize = true;
                }
                if (!flagForSize) {
                    System.err.println("Codebook of " + codebook.length + " clusters will be removed as such number of clusters is not selected!!");
                    codebooks.remove(codebook);
                }
            }
            if (!appending) {
                for (Cluster[] codebook : codebooks) {
                    try {
                        Cluster.writeClusters(codebook, indexPath + ".config/" + ((SimpleExtractor) extractorItem.getExtractorInstance()).getFieldName() + codebook.length);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            this.SimpleExtractorsAndCodebooks.put(extractorItem, codebooks);

            for (int numOfCluster : numOfClusters) {
                found = false;
                for (Cluster[] codebook : codebooks) {
                    if (codebook.length == numOfCluster) {
                        found = true;
                    }
                }
                if (!found) this.sampling = true;
            }
        } else {
            throw new UnsupportedOperationException(globalFeatureClass.getSimpleName() + " with " + detector.name() + " already exists!!");
        }
    }

    /**
     * WARNING!! This can be used in order to use a different DocumentBuilder than the {@link GlobalDocumentBuilder}, {@link LocalDocumentBuilder} or the {@link SimpleDocumentBuilder}.
     * Every time only one custom DocumentBuilder can be used. At the same time, using the addExtractor methods one can add other builders to be used at the same time. BUT when using
     * a custom DocumentBuilder, sampling can be used for Local Features. This means that, if you want to use a {@link LocalDocumentBuilder} or {@link SimpleDocumentBuilder}, you can use
     * them, only combined with pre-computed codebooks!!
     *
     * @param customDocumentBuilder
     */
    public void setCustomDocumentBuilder(Class<? extends DocumentBuilder> customDocumentBuilder) {
        this.customDocumentBuilder = customDocumentBuilder;
        this.customDocBuilderFlag = true;
//        this.listForCustomDocumentBuilder = new HashSet<>(listOfGlobalExtractors.size());
//        boolean flag = true;
//        for(Class<? extends GlobalFeature> globalFeatureClass : listOfGlobalExtractors){
//            for (ExtractorItem next : listForCustomDocumentBuilder) {
//                if (next.getExtractorClass().equals(globalFeatureClass)) {
//                    flag = false;
//                }
//            }
//            if (flag) {
//                this.listForCustomDocumentBuilder.add(new ExtractorItem(globalFeatureClass));
//            } else {
//                throw new UnsupportedOperationException(globalFeatureClass.getSimpleName() + " already exists!!");
//            }
//        }
//        if (!(listForCustomDocumentBuilder.size() > 0)) throw new UnsupportedOperationException("Something is wrong");
    }


    public void run() {
        lockLists = true;
        try {
            long start = System.currentTimeMillis();
            StringBuilder sb = new StringBuilder(1024);
            sb.delete(0, sb.length());
            // create a BufferedOutputStream with a large buffer
            dos = new BufferedOutputStream(new FileOutputStream(new File(this.csvFilePath)), 1024 * 1024 * 8);

            sb.append("FileName");
            sb.append(",");
            sb.append(DocumentBuilder.FIELD_NAME_COLORLAYOUT);
            sb.append(",");
            sb.append(DocumentBuilder.FIELD_NAME_COLORLAYOUT+DocumentBuilder.HASH_FIELD_SUFFIX);
            sb.append(",");
            sb.append(DocumentBuilder.FIELD_NAME_EDGEHISTOGRAM);
            sb.append(",");
            sb.append(DocumentBuilder.FIELD_NAME_EDGEHISTOGRAM+DocumentBuilder.HASH_FIELD_SUFFIX);
            sb.append("\n");
            dos.write(sb.toString().getBytes());

            if (imageList == null) {
//                allImages = FileUtils.getAllImages(new File(imageDirectory), true); //TODO: change to readFileLines
                allImages = FileUtils.readFileLines(new File(imageDirectory), true);
            } else {
                allImages = new LinkedList<String>();
                BufferedReader br = new BufferedReader(new FileReader(imageList));
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().length() > 3) allImages.add(line.trim());
                }
            }

            if (!(allImages.size() > 0)) throw new UnsupportedOperationException("No images were found!!");

            for (int numOfCluster : numOfClusters) {
                numOfClustersSet.add(numOfCluster);
            }

            if (((LocalExtractorsAndCodebooks.size() > 0) || (SimpleExtractorsAndCodebooks.size() > 0)) && (!(numOfClustersSet.size() > 0))) {
                throw new UnsupportedOperationException("Need to set number of clusters for Local Extractors!!");
            }

            numImages = allImages.size();
            index();

            System.out.printf("Total time of indexing: %s.\n", convertTime(System.currentTimeMillis() - start));
            
            dos.close();

            indexingFinished = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void flushDocuments() {
        System.out.println("Flushing documents....");
        long start = System.currentTimeMillis();
        try {
            for (Map.Entry<String, Document> documentEntry : allDocuments.entrySet()) {
                writer.addDocument(documentEntry.getValue());
            }
            LuceneUtils.commitWriter(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.printf("Time of flushing: %s.\n", convertTime(System.currentTimeMillis() - start));
    }

    private void index() {
        System.out.printf("Indexing %d images\n", numImages);
        long start = System.currentTimeMillis();
        try {
            Thread p, c, m;
            p = new Thread(new Producer(allImages), "Producer");
            p.start();
            LinkedList<Thread> threads = new LinkedList<Thread>();
            for (int i = 0; i < numOfThreads; i++) {
                c = new Thread(new Consumer(), String.format("Consumer-%02d", i + 1));
                c.start();
                threads.add(c);
            }
            Monitoring monitoring = new Monitoring();
            m = new Thread(monitoring, "IndexingMonitor");
            m.start();
            for (Thread thread : threads) {
                thread.join();
            }
            monitoring.killMonitoring();
            long end = System.currentTimeMillis() - start;
            System.out.printf("Analyzed %d images in %s ~ %3.2f ms each.\n", overallCount, convertTime(end), ((overallCount > 0) ? ((float) end / (float) overallCount) : -1f));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void fillSampleWithGlobals() {
        System.out.println("Filling GlobalFeatures....");
        System.out.printf("Indexing %d images\n", sampleImages.size());
        long start = System.currentTimeMillis();
        try {
            Thread p, c, m;
            p = new Thread(new Producer(sampleImages));
            p.start();
            LinkedList<Thread> threads = new LinkedList<Thread>();
            for (int i = 0; i < numOfThreads; i++) {
                c = new Thread(new ConsumerForGlobalSample());
                c.start();
                threads.add(c);
            }
            Monitoring monitoring = new Monitoring();
            m = new Thread(monitoring);
            m.start();
            for (Thread thread : threads) {
                thread.join();
            }
            monitoring.killMonitoring();
            long end = System.currentTimeMillis() - start;
            System.out.printf("Analyzed %d images in %s ~ %3.2f ms each.\n", overallCount, convertTime(end), ((overallCount > 0) ? ((float) end / (float) overallCount) : -1f));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void sample(HashMap<ExtractorItem, LinkedList<Cluster[]>> mapWithClassesAndCodebooks) {
        long start, end;
        LinkedList<Thread> threads = new LinkedList<Thread>();
        Thread p, c, m;
        ExtractorForLocalSample extractorForLocalSample;
        Monitoring monitoring;
        Extractor myExtractor;
        String codebookTitle, toPrint;
        Cluster[] codebook;
        boolean flag;
        try {
            for (ExtractorItem extractorItem : mapWithClassesAndCodebooks.keySet()) {
                myExtractor = extractorItem.getExtractorInstance();

                conSampleMap.clear();
                threads.clear();

                if (extractorItem.isSimple()) {
                    codebookTitle = ((SimpleExtractor) myExtractor).getFieldName();
                    toPrint = ((SimpleExtractor) myExtractor).getFeatureName() + " and " + aggregator.getSimpleName();
                } else if (extractorItem.isLocal()) {
                    codebookTitle = (extractorItem.getFeatureInstance()).getFieldName();
                    toPrint = (extractorItem.getFeatureInstance()).getFeatureName() + " and " + aggregator.getSimpleName();
                } else throw new UnsupportedOperationException("Something is wrong!! (ParallelLocalIndexer.sampling)");

                System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                System.out.println("Feature: " + toPrint);

                p = new Thread(new Producer(sampleImages));
                p.start();
                start = System.currentTimeMillis();
                for (int i = 0; i < numOfThreads; i++) {
                    extractorForLocalSample = new ExtractorForLocalSample(extractorItem);
                    c = new Thread(extractorForLocalSample);
                    threads.add(c);
                    c.start();
                }
                monitoring = new Monitoring();
                m = new Thread(monitoring);
                m.start();
                for (Thread thread : threads) {
                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                monitoring.killMonitoring();
                end = System.currentTimeMillis() - start;
                System.out.printf("Analyzed %d images in %s ~ %3.2f ms each.\n", overallCount, convertTime(end), ((overallCount > 0) ? ((float) end / (float) overallCount) : -1f));

                for (Integer numOfClusters : numOfClustersSet) {
                    System.out.println("Number of clusters: " + numOfClusters);
                    flag = true;
                    for (int j = 0; j < mapWithClassesAndCodebooks.get(extractorItem).size(); j++) {
                        if (mapWithClassesAndCodebooks.get(extractorItem).get(j).length == numOfClusters) {
                            System.out.println("Codebook of " + numOfClusters + " clusters found, no need to generate!");
                            flag = false;
                        }
                    }

                    if (flag) {
                        start = System.currentTimeMillis();
                        codebook = codebookGenerator(conSampleMap, numOfClusters);
                        Cluster.writeClusters(codebook, indexPath + ".config/" + codebookTitle + numOfClusters);
                        mapWithClassesAndCodebooks.get(extractorItem).add(codebook);
                        System.out.printf("Time of codebook generation: %s.\n", convertTime(System.currentTimeMillis() - start));
                    }

                }

                threads.clear();


                p = new Thread(new ProducerForLocalSample(conSampleMap));
                p.start();
                start = System.currentTimeMillis();
                for (int i = 0; i < numOfThreads; i++) {
                    c = new Thread(new ConsumerForLocalSample(extractorItem, mapWithClassesAndCodebooks.get(extractorItem)));
                    threads.add(c);
                    c.start();
                }
                monitoring = new Monitoring();
                m = new Thread(monitoring);
                m.start();
                for (Thread thread : threads) {
                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                monitoring.killMonitoring();
                end = System.currentTimeMillis() - start;
                System.out.printf("Analyzed %d images in %s ~ %3.2f ms each.\n", overallCount, convertTime(end), ((overallCount > 0) ? ((float) end / (float) overallCount) : -1f));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public double getPercentageDone() {
        return (double) overallCount / (double) numImages;
    }

    public ImagePreprocessor getImagePreprocessor() {
        return imagePreprocessor;
    }

    public void setImagePreprocessor(ImagePreprocessor imagePreprocessor) {
        this.imagePreprocessor = imagePreprocessor;
    }

    class Producer implements Runnable {
        private List<String> localList;

        public Producer(List<String> localList) {
            this.localList = localList;
            overallCount = 0;
            queue.clear();
        }

        public void run() {
            File next;
            for (String path : localList) {
                next = new File(path);
                try {
                    // option 1 --------------------
//                    byte[] buffer = Files.readAllBytes(Paths.get(path)); // JDK 7 only!
                    // option 2 --------------------
//                    path = next.getCanonicalPath();
//                    int fileSize = (int) next.length();
//                    byte[] buffer = new byte[fileSize];
//                    FileInputStream fis = new FileInputStream(next);
//                    int tmp = fis.read(buffer);
//                    assert(tmp == fileSize);
//                    fis.close();
                    // option 3 --------------------
                    int fileSize = (int) next.length();
                    byte[] buffer = new byte[fileSize];
                    FileInputStream fis = new FileInputStream(next);
                    FileChannel channel = fis.getChannel();
                    MappedByteBuffer map = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
                    map.load();
                    map.get(buffer);
                    queue.put(new WorkItem(path, buffer));
                    channel.close();
                    fis.close();
                } catch (Exception e) {
                    System.err.println("Could not open " + path + ". " + e.getMessage());
                }
            }
            String path = null;
            byte[] buffer = null;
            for (int i = 0; i < numOfThreads * 3; i++) {
                try {
                    queue.put(new WorkItem(path, buffer));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class ProducerForLocalSample implements Runnable {
        private ConcurrentHashMap<String, List<? extends LocalFeature>> localSampleList;

        public ProducerForLocalSample(ConcurrentHashMap<String, List<? extends LocalFeature>> localSampleList) {
            this.localSampleList = localSampleList;
            overallCount = 0;
            queue.clear();
        }

        public void run() {
            for (Map.Entry<String, List<? extends LocalFeature>> listEntry : localSampleList.entrySet()) {
                try {
                    queue.put(new WorkItem(listEntry.getKey(), listEntry.getValue()));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            String path = null;
            List<? extends LocalFeature> listOfFeatures = null;
            for (int i = 0; i < numOfThreads * 3; i++) {
                try {
                    queue.put(new WorkItem(path, listOfFeatures));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class ExtractorForLocalSample implements Runnable {
        private AbstractLocalDocumentBuilder documentBuilder;
        private ExtractorItem extractorItem;
        private boolean locallyEnded;

        public ExtractorForLocalSample(ExtractorItem extractorItem) {
            if (extractorItem.isLocal()) documentBuilder = new LocalDocumentBuilder();
            else if (extractorItem.isSimple()) documentBuilder = new SimpleDocumentBuilder();
            else throw new UnsupportedOperationException("Something is wrong!! (ExtractorForLocalSample)");
            this.extractorItem = extractorItem.clone();
            this.locallyEnded = false;
        }

        public void run() {
            WorkItem tmp;
            ByteArrayInputStream b;
            while (!locallyEnded) {
                try {
                    tmp = queue.take();
                    if (tmp.getFileName() == null) locallyEnded = true;
                    else overallCount++;
                    if (!locallyEnded) {   //&& tmp != null
                        b = new ByteArrayInputStream(tmp.getBuffer());
                        BufferedImage image = ImageIO.read(b);
                        if(imagePreprocessor != null){
                            image = imagePreprocessor.process(image);
                        }
                        conSampleMap.put(tmp.getFileName(), (documentBuilder.extractLocalFeatures(image, ((LocalFeatureExtractor) extractorItem.getExtractorInstance())).getFeatures()));
                    }
                } catch (InterruptedException | IOException e) {
                    log.severe(e.getMessage());
                }  catch (Exception e) {
                    log.severe(e.getMessage());
                }
            }
        }

    }

    class ConsumerForLocalSample implements Runnable {
        private AbstractLocalDocumentBuilder documentBuilder;
        private ExtractorItem localExtractorItem;
        private LinkedList<Cluster[]> clusters;
        private boolean locallyEnded;

        public ConsumerForLocalSample(ExtractorItem extractorItem, LinkedList<Cluster[]> clusters) {
            ExtractorItem tmpExtractorItem = extractorItem.clone();
            if (extractorItem.isLocal())
                documentBuilder = new LocalDocumentBuilder(tmpExtractorItem, clusters, aggregator);
            else if (extractorItem.isSimple())
                documentBuilder = new SimpleDocumentBuilder(tmpExtractorItem, clusters, aggregator);
            else throw new UnsupportedOperationException("Something is wrong!! (ConsumerForLocalSample)");

            this.localExtractorItem = tmpExtractorItem;
            this.clusters = clusters;
            this.locallyEnded = false;
        }

        public void run() {
            WorkItem tmp;
            Field[] fields;
            Document doc;
            while (!locallyEnded) {
                try {
                    tmp = queue.take();
                    if (tmp.getFileName() == null) locallyEnded = true;
                    else overallCount++;
                    if (!locallyEnded) {   //&& tmp != null
                        fields = documentBuilder.createLocalDescriptorFields(tmp.getListOfFeatures(), localExtractorItem, clusters);
                        doc = allDocuments.get(tmp.getFileName());
                        for (Field field : fields) {
                            doc.add(field);
                        }
                    }
                } catch (InterruptedException e) {
                    log.severe(e.getMessage());
                }  catch (Exception e) {
                    log.severe(e.getMessage());
                }
            }
        }
    }

    class ConsumerForGlobalSample implements Runnable {
        private GlobalDocumentBuilder globalDocumentBuilder;
        private boolean locallyEnded;

        public ConsumerForGlobalSample() {
            this.globalDocumentBuilder = new GlobalDocumentBuilder(globalHashing, globalHashingMode, useDocValues);
            for (ExtractorItem globalExtractor : GlobalExtractors) {
                this.globalDocumentBuilder.addExtractor(globalExtractor.clone());
            }
            this.locallyEnded = false;
        }

        public void run() {
            WorkItem tmp;
            Field[] fields;
            Document doc;
            while (!locallyEnded) {
                try {
                    tmp = queue.take();
                    if (tmp.getFileName() == null) locallyEnded = true;
                    else overallCount++;
                    if (!locallyEnded) {   //&& tmp != null
                        BufferedImage image = ImageIO.read(new ByteArrayInputStream(tmp.getBuffer()));
                        if(imagePreprocessor != null){
                            image = imagePreprocessor.process(image);
                        }
                        fields = globalDocumentBuilder.createDescriptorFields(image);
                        doc = allDocuments.get(tmp.getFileName());
                        for (Field field : fields) {
                            doc.add(field);
                        }
                    }
                } catch (InterruptedException | IOException e) {
                    log.severe(e.getMessage());
                } catch (Exception e) {
                    log.severe(e.getMessage());
                }
            }
        }
    }

    class Consumer implements Runnable {
        private LocalDocumentBuilder localDocumentBuilder;
        private SimpleDocumentBuilder simpleDocumentBuilder;
        private GlobalDocumentBuilder globalDocumentBuilder;
        private DocumentBuilder localCustomDocumentBuilder;
        private boolean locallyEnded;
        private StringBuilder sb = new StringBuilder(1024);

        public Consumer() {
            this.localDocumentBuilder = new LocalDocumentBuilder(aggregator);
            this.simpleDocumentBuilder = new SimpleDocumentBuilder(aggregator);
            this.globalDocumentBuilder = new GlobalDocumentBuilder(globalHashing, globalHashingMode, useDocValues);

            for (Map.Entry<ExtractorItem, LinkedList<Cluster[]>> listEntry : LocalExtractorsAndCodebooks.entrySet()) {
                this.localDocumentBuilder.addExtractor(listEntry.getKey().clone(), listEntry.getValue());
            }
            for (Map.Entry<ExtractorItem, LinkedList<Cluster[]>> listEntry : SimpleExtractorsAndCodebooks.entrySet()) {
                this.simpleDocumentBuilder.addExtractor(listEntry.getKey().clone(), listEntry.getValue());
            }
            for (ExtractorItem globalExtractor : GlobalExtractors) {
                this.globalDocumentBuilder.addExtractor(globalExtractor.clone());
            }

            try {
                if (customDocumentBuilder != null) {
                    this.localCustomDocumentBuilder = customDocumentBuilder.newInstance();
                } else this.localCustomDocumentBuilder = new GlobalDocumentBuilder(false);
            } catch (InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }

            this.locallyEnded = false;
        }

        public void run() {
            WorkItem tmp;
            HashMap<String, String> doc = new HashMap<String, String>();
            BufferedImage image;
            while (!locallyEnded) {
                try {
                    if (queue.peek()==null) {
//                        while (queue.remainingCapacity() > 2*queueCapacity/3) Thread.sleep(1000);
                        Thread.sleep((long) ((Math.random()/2+0.5) * 10000)); // sleep for a second if queue is empty.
                    }
                    tmp = queue.take();
                    if (tmp.getFileName() == null) locallyEnded = true;
                    else overallCount++;
                    if (!locallyEnded) {    //&& tmp != null
                        sb.delete(0, sb.length());
                        image = ImageIO.read(new ByteArrayInputStream(tmp.getBuffer()));
                        if(imagePreprocessor != null){
                            image = imagePreprocessor.process(image);
                        }
                        Path p = Paths.get(tmp.getFileName());
                        sb.append(p.getFileName().toString()+",");
                        doc = globalDocumentBuilder.createDescriptorFields(image, true);
                        sb.append(doc.get(DocumentBuilder.FIELD_NAME_COLORLAYOUT));
                        sb.append(",");
                        sb.append(doc.get(DocumentBuilder.FIELD_NAME_COLORLAYOUT+DocumentBuilder.HASH_FIELD_SUFFIX));
                        sb.append(",");
                        sb.append(doc.get(DocumentBuilder.FIELD_NAME_EDGEHISTOGRAM));
                        sb.append(",");
                        sb.append(doc.get(DocumentBuilder.FIELD_NAME_EDGEHISTOGRAM+DocumentBuilder.HASH_FIELD_SUFFIX));

                        sb.append("\n");
                        synchronized (dos) {
                            dos.write(sb.toString().getBytes());
                            // dos.flush();  // flushing takes too long ... better not.
                        }
                    }
                } catch (InterruptedException | IOException e) {
                    log.severe(e.getMessage());
                } catch (Exception e) {
                    log.severe(e.getMessage());
                }
            }
        }
    }

    class Monitoring implements Runnable {
        private boolean killMonitor;

        public Monitoring() {
            this.killMonitor = false;
        }

        public void run() {
            long end, gap = 1000 * monitoringInterval;
            long start = System.currentTimeMillis();
            try {
                Thread.sleep(gap); // wait xx seconds
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            while (!killMonitor) {
                try {
                    // print the current status:
                    end = System.currentTimeMillis() - start;
                    System.out.printf("Analyzed %d images in %s ~ %3.2f ms each. (queue size is %d)\n", overallCount, convertTime(end), ((overallCount > 0) ? ((float) end / (float) overallCount) : -1f), queue.size());
                    Thread.sleep(gap); // wait xx seconds
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public void killMonitoring() {
            this.killMonitor = true;
        }
    }

    private LinkedList<String> selectVocabularyDocs(int maxDocs, int capacity) {
        // need to make sure that this is not running forever ......
        LinkedList<String> tmpImages = new LinkedList<String>();
        Document doc;
        String tmpStr;
        if (numOfDocsForCodebooks >= maxDocs) {
            for (int i = 0; i < maxDocs; i++) {
                tmpStr = allImages.get(i);
                doc = new Document();
                doc.add(new StringField(DocumentBuilder.FIELD_NAME_IDENTIFIER, tmpStr, Field.Store.YES));
                allDocuments.put(tmpStr, doc);
                tmpImages.add(tmpStr);
            }
            allImages.clear();
        } else {
            int tmpIndex;
            for (int i = 0; i < capacity; i++) {
                tmpIndex = (int) Math.floor(Math.random() * (double) allImages.size());
                tmpStr = allImages.get(tmpIndex);
                doc = new Document();
                doc.add(new StringField(DocumentBuilder.FIELD_NAME_IDENTIFIER, tmpStr, Field.Store.YES));
                allDocuments.put(tmpStr, doc);
                tmpImages.add(tmpStr);
                allImages.remove(tmpIndex);
            }
        }
        return tmpImages;
    }

    private Cluster[] codebookGenerator(ConcurrentHashMap<String, List<? extends LocalFeature>> sampleMap, int numClusters) {
        KMeans k;
        if (useParallelClustering) k = new ParallelKMeans(numClusters);
        else k = new KMeans(numClusters);
        // fill the KMeans object:
        List<? extends LocalFeature> tempList;
        for (Map.Entry<String, List<? extends LocalFeature>> stringListEntry : sampleMap.entrySet()) {
            tempList = stringListEntry.getValue();
            for (LocalFeature aTempList : tempList) {
                k.addFeature(aTempList.getFeatureVector());
            }
        }
        if (pm != null) { // set to 5 of 100 before clustering starts.
            pm.setProgress(5);
            pm.setNote("Starting clustering");
        }
        if (k.getFeatureCount() < numClusters) {
            // this cannot work. You need more data points than clusters.
            throw new UnsupportedOperationException("Only " + k.getFeatureCount() + " features found to cluster in " + numClusters + ". Try to use less clusters or more images.");
        }
        // do the clustering:
        System.out.println("Number of local features: " + df.format(k.getFeatureCount()));
        System.out.println("Starting clustering ...");
        k.init();
        System.out.println("Step.");
        long start = System.currentTimeMillis();
        double lastStress = k.clusteringStep();

        if (pm != null) { // set to 8 of 100 after first step.
            pm.setProgress(8);
            pm.setNote("Step 1 finished");
        }

        System.out.println(convertTime(System.currentTimeMillis() - start) + " -> Next step.");
        start = System.currentTimeMillis();
        double newStress = k.clusteringStep();

        if (pm != null) { // set to 11 of 100 after second step.
            pm.setProgress(11);
            pm.setNote("Step 2 finished");
        }

        // critical part: Give the difference in between steps as a constraint for accuracy vs. runtime trade off.
        double threshold = Math.max(20d, (double) k.getFeatureCount() / 1000d);
        System.out.println("Threshold = " + df.format(threshold));
        int cStep = 3;

        while (Math.abs(newStress - lastStress) > threshold && cStep < 12) {
            System.out.println(convertTime(System.currentTimeMillis() - start) + " -> Next step. Stress difference ~ |" + (int) newStress + " - " + (int) lastStress + "| = " + df.format(Math.abs(newStress - lastStress)));
            start = System.currentTimeMillis();
            lastStress = newStress;
            newStress = k.clusteringStep();
            if (pm != null) { // set to XX of 100 after second step.
                pm.setProgress(cStep * 3 + 5);
                pm.setNote("Step " + cStep + " finished");
            }
            cStep++;
        }

        return k.getClusters();
    }

    private String convertTime(long time) {
        double h = time / 3600000.0;
        double m = (h - Math.floor(h)) * 60.0;
        double s = (m - Math.floor(m)) * 60;

//        return String.format("%02d:%02d:%02d", hour, minutes, seconds);
        return String.format("%s%02d:%02d", (((int) h > 0) ? String.format("%02d:", (int) h) : ""), (int) m, (int) s);
    }

    public boolean hasEnded() {
        return indexingFinished;
    }
}
