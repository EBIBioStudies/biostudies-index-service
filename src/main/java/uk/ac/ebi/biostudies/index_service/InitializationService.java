package uk.ac.ebi.biostudies.index_service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.analysis.AnalyzerManager;
import uk.ac.ebi.biostudies.index_service.autocomplete.EFOTermMatcher;
import uk.ac.ebi.biostudies.index_service.index.management.IndexInitializerFactory;
import uk.ac.ebi.biostudies.index_service.messages.WebSocketConnectionService;
import uk.ac.ebi.biostudies.index_service.parsing.ParserManager;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;
import uk.ac.ebi.biostudies.index_service.registry.service.CollectionRegistryService;

/**
 * Service responsible for initializing application components upon startup.
 *
 * <p>This service listens for the {@link ApplicationReadyEvent} and orchestrates the initialization
 * sequence of:
 *
 * <ul>
 *   <li>Collection registry loading and validation
 *   <li>Analyzer and parser initialization
 *   <li>Index opening and initialization (standard and EFO indexes)
 *   <li>WebSocket connection establishment for real-time messaging
 * </ul>
 *
 * <p>The initialization order is critical: indexes must be ready before WebSocket connections begin
 * receiving submission messages.
 */
@Slf4j
@Service
public class InitializationService {

  private final CollectionRegistryService collectionRegistryService;
  private final AnalyzerManager analyzerManager;
  private final ParserManager parserManager;
  private final IndexInitializerFactory initializerFactory;
  private final TaxonomyManager taxonomyManager;
  private final WebSocketConnectionService webSocketConnectionService;
  private final EFOTermMatcher efoTermMatcher;

  public InitializationService(
      CollectionRegistryService collectionRegistryService,
      AnalyzerManager analyzerManager,
      ParserManager parserManager,
      IndexInitializerFactory initializerFactory,
      TaxonomyManager taxonomyManager,
      WebSocketConnectionService webSocketConnectionService,
      EFOTermMatcher efoTermMatcher) {
    this.collectionRegistryService = collectionRegistryService;
    this.analyzerManager = analyzerManager;
    this.parserManager = parserManager;
    this.initializerFactory = initializerFactory;
    this.taxonomyManager = taxonomyManager;
    this.webSocketConnectionService = webSocketConnectionService;
    this.efoTermMatcher = efoTermMatcher;
  }

  /**
   * Initializes all necessary components once the Spring application context is fully started.
   *
   * <p>This method is triggered automatically when the application publishes an {@link
   * ApplicationReadyEvent}. The initialization sequence is:
   *
   * <ol>
   *   <li>Load collection registry configuration
   *   <li>Initialize analyzers based on registry requirements
   *   <li>Initialize parsers based on registry requirements
   *   <li>Open and initialize standard indexes
   *   <li>Initialize EFO (Experimental Factor Ontology) index
   *   <li>Establish WebSocket connection for real-time messaging
   * </ol>
   *
   * @throws IllegalStateException if any error occurs during initialization
   */
  @EventListener(ApplicationReadyEvent.class)
  public void initialize() {
    log.debug("Application initialization started");

    try {
      // Load and validate collection registry
      CollectionRegistry collectionRegistry = collectionRegistryService.loadRegistry();
      log.debug("Collection registry loaded successfully");

      // Create the analyzers required by collection registry
      analyzerManager.init(collectionRegistry);
      log.debug("Analyzers initialized");

      // Create the parsers required by collection registry
      parserManager.init(collectionRegistry);
      log.debug("Parsers initialized");

      // Open and initialize main and standard indices first
      log.info("Initializing Lucene indexes...");
      initializerFactory.initializeAllIndexes();
      log.info("All indexes initialized successfully");

      // Initialize EFO caches. It uses the EFO index so order matters.
      efoTermMatcher.initialize();

      // Init configuration for facets
      taxonomyManager.init(collectionRegistry);

      // Finally, establish WebSocket connection for receiving messages
      webSocketConnectionService.openWebsocket();

      log.info("Application initialization completed successfully");

    } catch (Exception e) {
      log.error("Application initialization failed", e);
      throw new IllegalStateException("Failed to initialize application components", e);
    }
  }
}
