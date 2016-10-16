package moe.wait;

import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import net.semanticmetadata.lire.utils.*;
import net.semanticmetadata.lire.aggregators.*;
import net.semanticmetadata.lire.indexers.tools.*;
import net.semanticmetadata.lire.indexers.parallel.*;
import net.semanticmetadata.lire.imageanalysis.features.global.*;
import net.semanticmetadata.lire.imageanalysis.features.local.simple.*;
import net.semanticmetadata.lire.imageanalysis.features.local.opencvfeatures.*;
import net.semanticmetadata.lire.builders.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import java.util.regex.*;
import java.nio.file.*;

public class VideoIndexer {

    private static void printHelp() {
        System.out.println("This help text is shown if you start the ParallelSolrIndexer with the '-h' option.\n" +
                "\n" +
                "$> VideoIndexer -i <infile> [-o <outfile>] [-n <threads>] [-f] [-m <thumbnail_max_height>] [-r <fps>] \\\\ \n" +
                "         [-y <list of feature classes>]\n" +
                "\n" +
                "Note: if you don't specify an outfile, input file name with \".csv\" is used for output.\n" +
                "\n" +
                "-n ... number of threads should be something your computer can cope with. default is DocumentBuilder.NUM_OF_THREADS.\n" +
                "-f ... forces overwrite of outfile\n" +
                "-m ... maximum height of thumbnail generated for indexed. Default is 120.\n" +
                "-r ... thumbnail extraction fps. Default is extract all frames\n");
    }

    public static void main(String[] args) throws IOException {
        // Checking if arg[0] is there and if it is a directory.
        boolean passed = false;
        String inputFile = null;
        String outputFile = null;
        boolean forceOverwrite = false;
        float fps = 23.976f;
        int thumbnail_max_height = 120;
        int numberOfThreads = DocumentBuilder.NUM_OF_THREADS;
        String thumbnailIDFormat = "%08d";
        String thumbnailFileFormat = ".jpg";

        // parse programs args ...
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("-i")) {
                // infile ...
                if ((i + 1) < args.length)
                    inputFile = args[i + 1];
                else {
                    System.err.println("Could not set out file.");
                    printHelp();
                }
            } else if (arg.startsWith("-o")) {
                // out file, if it's not set, use the input filename with csv extension.
                if ((i + 1) < args.length)
                    outputFile = args[1];
                else printHelp();
            } else if (arg.startsWith("-m")) {
                if ((i + 1) < args.length)
                    try {
                        thumbnail_max_height = Integer.parseInt(args[i + 1]);
                    } catch (Exception e1) {
                        System.err.println("Could not set thumbnail_max_height to \"" + args[i + 1] + "\".");
                        e1.printStackTrace();
                    }
                else printHelp();
            } else if (arg.startsWith("-r")) {
                if ((i + 1) < args.length)
                    try {
                        fps = Float.parseFloat(args[i + 1]);
                    } catch (Exception e1) {
                        System.err.println("Could not set fps to \"" + args[i + 1] + "\".");
                        e1.printStackTrace();
                    }
                else printHelp();
            } else if (arg.startsWith("-f") || arg.startsWith("--force")) {
                forceOverwrite = true;
            } /*else if (arg.startsWith("-y") || arg.startsWith("--features")) {
                if ((i + 1) < args.length) {
                    // parse and check the features.
                    String[] ft = args[i + 1].split(",");
                    for (int j = 0; j < ft.length; j++) {
                        String s = ft[j].trim();
                        if (FeatureRegistry.getClassForCode(s)!=null) {
                            e.addFeature(FeatureRegistry.getClassForCode(s));
                        }
                    }
                }
            }*/ else if (arg.startsWith("-h")) {
                printHelp();
                System.exit(0);
            } else if (arg.startsWith("-n")) {
                if ((i + 1) < args.length)
                    try {
                        numberOfThreads = Integer.parseInt(args[i + 1]);
                    } catch (Exception e1) {
                        System.err.println("Could not set number of threads to \"" + args[i + 1] + "\".");
                        e1.printStackTrace();
                    }
                else printHelp();
            }
        }

        if (outputFile == null) {
            outputFile = FilenameUtils.getBaseName(inputFile)+".csv";
        }

        File f = new File(inputFile);
        if (!f.exists() || !f.isFile()){
            System.out.println("Input is not a file");
            System.exit(1);
        }
    
        f = new File(outputFile);
        if (!forceOverwrite && f.exists() && f.isFile()) {
            System.out.println("Output file already exists");
            System.exit(1);
        }

        System.out.println("Preparing temp directory");
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("lire_");
            System.out.println("Working temp directory: " + tempDir.toString());
        } catch (IOException e) {
            System.out.println("Failed to create temp directory");
            System.exit(1);
        }

        System.out.println("Extracting thumbnails");
        HashMap<String, String> timeCodeMap = new HashMap<String, String>();

        try {
            Path extractPath = Paths.get(tempDir.toString(), thumbnailIDFormat+thumbnailFileFormat);
            String vf = null;
            vf = "fps="+Float.toString(fps)+",scale=-1:"+Integer.toString(thumbnail_max_height)+",showinfo";

            String[] commands = {"ffmpeg", "-i", inputFile, "-q:v", "2", "-an", "-vf", vf, extractPath.toString()};
            Process proc = Runtime.getRuntime().exec(commands);

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

            String stderr = null;
            int adjustment = 0;
            while ((stderr = stdError.readLine()) != null) {
                //System.out.println(stderr);
                Matcher matcher = Pattern.compile("n:\\s+0\\s+pts:\\s+(\\d+)\\spts_time:").matcher(stderr);
                if (matcher.find()) {
                    adjustment = Integer.parseInt(matcher.group(1));
                    for (int i = 1; i <= adjustment; i++) {
                        Path brokenFrames = Paths.get(tempDir.toString(), String.format(thumbnailIDFormat+thumbnailFileFormat, i));
                        System.out.println(brokenFrames.toString()+" deleted because it has no valid timecode");
                        Files.delete(brokenFrames);
                    }
                }
            }
            proc.waitFor();

            File[] files = tempDir.toFile().listFiles();
            float timeCode = 0f;
            for (File file : files) {
                timeCodeMap.put(file.getName(),String.format("%.2f", timeCode));
                timeCode += 1/fps*(files.length+adjustment)/files.length;
            }

        } catch (Exception e){
            System.out.println("Error extracting thumbnails");
            System.exit(1);
        }

        System.out.println("Analyzing images");
        Path csvPath = Paths.get(tempDir.toString(), "temp.csv");
        ParallelCsvIndexer indexer = new ParallelCsvIndexer(numberOfThreads, "index", tempDir.toString(), csvPath.toString());
        //Global
        indexer.addExtractor(ColorLayout.class);
        indexer.addExtractor(EdgeHistogram.class);
        //indexer.addExtractor(CEDD.class);
        //indexer.addExtractor(FCTH.class);
        //indexer.addExtractor(AutoColorCorrelogram.class);
        //Local
        //indexer.addExtractor(CvSurfExtractor.class);
        //indexer.addExtractor(CvSiftExtractor.class);
        //Simple
        //indexer.addExtractor(CEDD.class, SimpleExtractor.KeypointDetector.CVSURF);
        //indexer.addExtractor(JCD.class, SimpleExtractor.KeypointDetector.Random);
        indexer.run();

        Map<Integer, String> docMap = new TreeMap<Integer, String>();

        try {
            FileWriter fw = new FileWriter(outputFile);
            BufferedReader br = new BufferedReader(new FileReader(csvPath.toString()));
            BufferedWriter bw = new BufferedWriter(fw);
            String line;

            br.readLine();
            while((line = br.readLine()) != null) {
                String[] columns = line.split(thumbnailFileFormat+",");
                docMap.put(Integer.parseInt(columns[0]), timeCodeMap.get(columns[0]+thumbnailFileFormat)+","+columns[1]);
            }
            br.close();

            List<String> list = new ArrayList<String>(docMap.values());
            for (String str : list) {
                bw.write(str);
                bw.newLine();
            }
            bw.flush();
            bw.close();

            Files.delete(csvPath);

        } catch (IOException e) {
            e.printStackTrace();
        }


        try {
            FileUtils.deleteDirectory(tempDir.toFile());
        }
        catch (Exception e){
            e.printStackTrace();
        }

        System.out.println("Finished indexing.");
    }
}