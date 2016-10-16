# LIRE - Lucene Image Retrieval (With Video Indexer)
This is a fork of [LIRE](https://github.com/dermotte/LIRE) with support for indexing video files.

## System requirements
- [ffmpeg](https://www.ffmpeg.org/) (should work with any version)
- [Ant](http://ant.apache.org/)

Once you have configured correctly, you should be able to invoke `ffmpeg -version` and `ant -version` in any working directory.

## Building
```
git clone https://github.com/soruly/LIRE.git
cd LIRE
ant dist
```
### Example
Make sure you have set the class path correctly.

(linux) See help
```
java -cp "/home/soruly/project/LIRE/dist/*:/home/soruly/project/LIRE/dist/lib/*" moe.wait.VideoIndexer -h
```

(windows) extracting 120px thumbnails at 12fps using 2 threads
```
java -cp "C:\projects\LIRE\dist\*;C:\projects\LIRE\dist\lib\*" moe.wait.VideoIndexer -i "input.mp4" -o "output.csv" -n 2 -f -m 120 -r 12
```
