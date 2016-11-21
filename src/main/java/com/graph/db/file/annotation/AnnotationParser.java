package com.graph.db.file.annotation;

import static com.graph.db.util.FileUtil.getAllJsonFiles;
import static com.graph.db.util.FileUtil.logLineNumber;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.graph.db.Parser;
import com.graph.db.file.GenericSubscriber;
import com.graph.db.file.annotation.domain.GeneticVariant;
import com.graph.db.file.annotation.subscriber.ConsequenceTermSubscriber;
import com.graph.db.file.annotation.subscriber.GeneToGeneticVariantSubscriber;
import com.graph.db.file.annotation.subscriber.GeneticVariantToTranscriptVariantSubscriber;
import com.graph.db.file.annotation.subscriber.TranscriptToTranscriptVariantSubscriber;
import com.graph.db.file.annotation.subscriber.TranscriptVariantSubscriber;
import com.graph.db.output.HeaderGenerator;
import com.graph.db.output.OutputFileType;

/**
 * Nodes
 * - GeneticVariant
 * - TranscriptVariant
 * - ConsequenceTerm
 * 
 * Relationships
 * - GeneToGeneticVariant
 * - GeneticVariantToTranscriptVariant
 * - TranscriptToTranscriptVariant
 */
public class AnnotationParser implements Parser {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationParser.class);
	
	private final String inputFolder;
	private final String outputFolder;

	private final Gson gson;
	
	private final ExecutorService threadPool;
	private final EventBus eventBus;

	private final List<GenericSubscriber<?>> subscribers;

	public AnnotationParser(String inputFolder, String outputFolder) {
		this.inputFolder = inputFolder;
		this.outputFolder = outputFolder;
		
		gson = createGson();
        
		threadPool = Executors.newFixedThreadPool(10);
		eventBus = new AsyncEventBus(threadPool);
		
		subscribers = createSubscribers(outputFolder);
	}
	
	private Gson createGson() {
		GsonBuilder b = new GsonBuilder();
        b.registerTypeAdapter(GeneticVariant.class, new CustomJsonDeserializer());
        return b.create();
	}

	private List<GenericSubscriber<? extends Object>> createSubscribers(String outputFolder) {
        GeneToGeneticVariantSubscriber geneToGeneticVariantSubscriber = new GeneToGeneticVariantSubscriber(outputFolder, getClass());
        GenericSubscriber<Object> geneticVariantSubscriber = new GenericSubscriber<Object>(outputFolder, getClass(), OutputFileType.GENETIC_VARIANT);
        TranscriptVariantSubscriber transcriptVariantSubscriber = new TranscriptVariantSubscriber(outputFolder, getClass());
        GeneticVariantToTranscriptVariantSubscriber geneticVariantToTranscriptVariantSubscriber = new GeneticVariantToTranscriptVariantSubscriber(outputFolder, getClass());
        TranscriptToTranscriptVariantSubscriber transcriptToTranscriptVariantSubscriber = new TranscriptToTranscriptVariantSubscriber(outputFolder, getClass());
        ConsequenceTermSubscriber consequenceTermSubscriber = new ConsequenceTermSubscriber(outputFolder, getClass());
        
		return Arrays.asList(geneToGeneticVariantSubscriber, geneticVariantSubscriber, transcriptVariantSubscriber,
				geneticVariantToTranscriptVariantSubscriber, transcriptToTranscriptVariantSubscriber,
				consequenceTermSubscriber);
	}

	@Override
	public void execute() {
		File[] jsonFiles = getAllJsonFiles(inputFolder);

		registerSubscribers();
		
		for (File jsonFile : jsonFiles) {
			LOGGER.info("Processing file: {}", jsonFile);
			
			try (LineNumberReader reader = new LineNumberReader(new FileReader(jsonFile));) {
				String line;
				
				while (( line = reader.readLine()) != null) {
					logLineNumber(reader, 1000);
					
					GeneticVariant geneticVariant = gson.fromJson(line, GeneticVariant.class);
					eventBus.post(geneticVariant);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		
		shutDownThreadPool();
		closeSubscribers();
		
		generateHeaderFiles();
	}

	private void registerSubscribers() {
		subscribers.forEach(subscriber -> eventBus.register(subscriber));
	}

	private void shutDownThreadPool() {
		threadPool.shutdown();
		try {
			threadPool.awaitTermination(10, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	
	private void closeSubscribers() {
		subscribers.forEach(subscriber -> subscriber.close());
	}

	private void generateHeaderFiles() {
		EnumSet<OutputFileType> outputFileTypes = EnumSet.of(OutputFileType.GENETIC_VARIANT,
				OutputFileType.GENE_TO_GENETIC_VARIANT, OutputFileType.TRANSCRIPT_VARIANT,
				OutputFileType.GENETIC_VARIANT_TO_TRANSCRIPT_VARIANT, OutputFileType.TRANSCRIPT_TO_TRANSCRIPT_VARIANT,
				OutputFileType.CONSEQUENCE_TERM);
		new HeaderGenerator().generateHeaders(outputFolder, outputFileTypes);
	}

	public static void main(String[] args) {
		if ((args != null) && (args.length != 2)) {
			throw new RuntimeException("Incorrect args: $1=inputFolder, $2=outputFolder");
		}
		new AnnotationParser(args[0], args[1]).execute();
		LOGGER.info("Finished");
	}
}
