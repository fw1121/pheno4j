package com.graph.db.util;

import static com.graph.db.util.Constants.POISON_PILL;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileUtil {
	
    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtil.class);
    
	private FileUtil() {}
	
	public static void writeOutCsvHeader(String outputFolder, String fileName, Collection<String> collection) {
		String pathname = outputFolder + File.separator + fileName + "-header.csv";
		writeFile(collection, pathname);
	}
	
	public static void writeOutCsvFile(String outputFolder, Class<?> c, String fileName, Collection<String> collection) {
		String pathname = outputFolder + File.separator + fileName + Constants.HYPHEN + c.getSimpleName() + ".csv";
		writeFile(collection, pathname);
	}
	
	private static void writeFile(Collection<String> collection, String pathname) {
		LOGGER.info("Writing out: {}", pathname);
		try {
			FileUtils.writeLines(new File(pathname), collection);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static File[] getAllJsonFiles(String folder) {
		File dir = new File(folder);
		FilenameFilter filter = (directory, name) -> name.toLowerCase().endsWith(".json");
		return dir.listFiles(filter);
	}
	
	public static void logLineNumber(LineNumberReader reader, int threshold) {
		if ((reader.getLineNumber() % threshold) == 0) {
			LOGGER.info("Processed {} lines", reader.getLineNumber());
		}
	}
	
	/**
	 * Consumer of the queue will terminate when it processes the "poison pill"
	 */
	public static void sendPoisonPillToQueue(BlockingQueue<String> queue) {
		try {
			queue.put(POISON_PILL);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static List<String> getLines(String fileName) {
		return getLines(new File(fileName));
	}
	
	public static List<String> getLines(String fileName, boolean skipHeader) {
		List<String> lines = getLines(new File(fileName));
		if (skipHeader) {
			lines.remove(0);
		}
		return lines;
	}
	
	@SuppressWarnings("unchecked")
	public static List<String> getLines(File file) {
		try {
			return FileUtils.readLines(file);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static LineNumberReader createLineNumberReaderForGzipFile(String fileName) {
		InputStream gzipStream;
		try {
			InputStream fileStream = new FileInputStream(fileName);
			gzipStream = new GZIPInputStream(fileStream);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		Reader decoder = new InputStreamReader(gzipStream);
		return new LineNumberReader(decoder);
	}
}
