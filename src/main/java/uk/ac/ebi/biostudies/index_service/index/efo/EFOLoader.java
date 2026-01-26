package uk.ac.ebi.biostudies.index_service.index.efo;

import com.google.common.io.CharStreams;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.semanticweb.HermiT.Reasoner;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.config.EFOConfig;

/**
 * Loads EFO OWL file, parses ontology, builds filtered {@link EFOModel}, provides {@link
 * EFOTermResolver}.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Locates EFO OWL (external file + bundled fallback)
 *   <li>Parses OWL using HermiT reasoner for hierarchy inference
 *   <li>Filters ignored classes via configurable ignore-list
 *   <li>Caches resolver for repeated use
 * </ul>
 *
 * <p>Thread-safe lazy loading. Call {@link #getResolver()} for cached access or {@link
 * #rebuildResolver()} to force reload (e.g., new EFO version).
 */
@Slf4j
@Component
public class EFOLoader {
  private static final IRI IRI_AE_LABEL = IRI.create("http://www.ebi.ac.uk/efo/ArrayExpress_label");
  private static final IRI IRI_EFO_URI = IRI.create("http://www.ebi.ac.uk/efo/EFO_URI");
  private static final IRI IRI_ALT_TERM =
      IRI.create("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym");
  private static final IRI IRI_ORG_CLASS =
      IRI.create("http://www.ebi.ac.uk/efo/organizational_class");
  private static final IRI IRI_PART_OF = IRI.create("http://www.obofoundry.org/ro/ro.owl#part_of");
  private static final IRI IRI_VERSION_INFO =
      IRI.create("http://www.w3.org/2002/07/owl#versionInfo");

  private final EFOConfig config;
  private volatile EFOTermResolver resolver;

  public EFOLoader(EFOConfig config) {
    this.config = config;
  }

  /** Returns cached resolver (lazy-built on first call). Thread-safe via double-checked locking. */
  public EFOTermResolver getResolver() {
    if (resolver == null) {
      synchronized (this) {
        if (resolver == null) {
          resolver = rebuildResolver();
        }
      }
    }
    return resolver;
  }

  /**
   * Forces complete rebuild: OWL parse → filter → resolver. Call when new EFO version detected or
   * index rebuild required.
   */
  public EFOTermResolver rebuildResolver() {
    synchronized (this) {
      log.info("Rebuilding EFO resolver");
      long start = System.currentTimeMillis();

      File efoFile = getEFOFile();
      if (efoFile == null) {
        throw new IllegalStateException("EFO file unavailable");
      }

      try (InputStream is = new FileInputStream(efoFile)) {
        log.info("Preparing to process {}", efoFile.toPath());
        LoadResult result = load(is); // Returns model + version
        log.info("EFO file processed. Removing not needed classes");
        EFOModel cleanModel = removeIgnoredClasses(result.model, config.getIgnoreList());
        resolver = new EFOTermResolver(cleanModel, result.version);

        log.info(
            "EFO rebuilt: {} nodes in {}ms (v{})",
            cleanModel.getNodeCount(),
            System.currentTimeMillis() - start,
            result.version);
        return resolver;
      } catch (IOException e) {
        throw new IllegalStateException("EFO rebuild failed", e);
      }
    }
  }

  /** Rebuilds model excluding nodes/relations in ignore-list. */
  private EFOModel removeIgnoredClasses(EFOModel model, String ignoreListLocation)
      throws IOException {
    if (ignoreListLocation == null) {
      return model;
    }

    Set<String> ignoreSet = loadIgnoreList(ignoreListLocation);
    log.debug("Ignoring {} EFO classes", ignoreSet.size());

    EFOModel.Builder builder = EFOModel.builder();

    // Copy non-ignored nodes
    model.getNodes().entrySet().stream()
        .filter(entry -> !ignoreSet.contains(entry.getKey()))
        .forEach(entry -> builder.addNode(entry.getValue()));

    // Copy non-ignored part-of relations
    model
        .getPartOfRelations()
        .forEach(
            (childId, parentIds) -> {
              if (!ignoreSet.contains(childId)) {
                parentIds.stream()
                    .filter(parentId -> !ignoreSet.contains(parentId))
                    .forEach(parentId -> builder.addPartOf(childId, parentId));
              }
            });

    EFOModel cleanModel = builder.build();
    log.debug("Filtered: {} → {} nodes", model.getNodeCount(), cleanModel.getNodeCount());
    return cleanModel;
  }

  private Set<String> loadIgnoreList(String location) throws IOException {
    try (InputStream is = new ClassPathResource(location).getInputStream()) {
      String[] lines =
          CharStreams.toString(new InputStreamReader(is, StandardCharsets.UTF_8))
              .split(System.lineSeparator());
      return new HashSet<>(Arrays.asList(lines));
    }
  }

  /** Locates EFO OWL file: external preferred, bundled fallback. */
//  private File getEFOFileX() {
//    File target = new File(config.getOwlFilename());
//    if (target.exists()) {
//      log.debug("Using external EFO: {}", config.getOwlFilename());
//      return target;
//    }
//
//    String bundlePath = config.getLocalOwlFilename();
//    log.warn("External missing, copying bundled {}", bundlePath);
//
//    Resource resource = new ClassPathResource(bundlePath);
//    try (InputStream is = resource.getInputStream()) {
//      Files.copy(is, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
//      if (target.exists() && target.length() > 0) {
//        log.info("Bundled EFO ready");
//        return target;
//      }
//    } catch (IOException e) {
//      log.error("Bundled copy failed", e);
//    }
//    return null;
//  }

  private File getEFOFile() {
    File external = new File(config.getOwlFilename());

    if (external.exists()) {
      log.info("External EFO file {} exists", config.getOwlFilename());
      return external;
    }

    // 2. Try download (with timeout)
    try {
      log.warn("External EFO file missing, attempting download...");
      downloadEFO(external);
      return external;
    } catch (IOException e) {
      log.error("Download failed: {}", e.getMessage());
    }

    // 3. Fail hard
    throw new IllegalStateException(
        "EFO unavailable. Manual action required: "
            + "1. Download https://www.ebi.ac.uk/efo/efo.owl\n"
            + "2. Place at "
            + config.getOwlFilename());
  }

  private void downloadEFO(File target) throws IOException {
    String url = config.getUpdateUrl();
    log.info("Downloading EFO from {} (~340 MB, may take 30s)", url);

    try {
      URI uri = URI.create(url);
      HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
      conn.setConnectTimeout(10_000); // 10s connect
      conn.setReadTimeout(120_000); // 2min read (large file)
      conn.setRequestProperty("User-Agent", "BioStudies-Indexer");

      int responseCode = conn.getResponseCode();
      if (responseCode != 200) {
        throw new IOException("EFO download failed: HTTP " + responseCode);
      }

      try (InputStream in = conn.getInputStream()) {
        long bytes = Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        log.info("Downloaded {} MB", bytes / 1_048_576);
      }
    } catch (IOException e) {
      if (target.exists()) target.delete(); // Cleanup partial
      throw e;
    }
  }

  /** Parses OWL stream into raw EFOModel with version metadata. */
  private LoadResult load(InputStream owlStream) {
    OWLOntologyManager mgr = OWLManager.createOWLOntologyManager();
    System.setProperty("entityExpansionLimit", "100000000");

    Map<String, Set<String>> reverseSubClassOf = new HashMap<>();
    Map<String, Set<String>> reversePartOf = new HashMap<>();

    try {
      OWLOntology ontology = mgr.loadOntologyFromOntologyDocument(owlStream);
      String version = extractVersion(ontology);
      log.info("Loaded EFO v{}", version);

      EFOModel.Builder builder = EFOModel.builder();

      OWLReasonerFactory reasonerFactory = new Reasoner.ReasonerFactory();
      OWLReasoner reasoner = reasonerFactory.createReasoner(ontology);

      try {
        for (OWLClass cls : ontology.getClassesInSignature()) {
          loadClass(ontology, reasoner, cls, builder, reverseSubClassOf, reversePartOf);
        }

        linkParentsAndChildren(builder, reverseSubClassOf);
        builder.buildPartOfRelations(reversePartOf);

        return new LoadResult(builder.build(), version);
      } finally {
        reasoner.dispose();
      }
    } catch (OWLOntologyCreationException e) {
      throw new IllegalArgumentException("Invalid OWL", e);
    }
  }

  private String extractVersion(OWLOntology ontology) {
    for (OWLAnnotation annotation : ontology.getAnnotations()) {
      if (IRI_VERSION_INFO.equals(annotation.getProperty().getIRI())) {
        OWLLiteral literal = (OWLLiteral) annotation.getValue();
        return literal.getLiteral();
      }
    }
    return "unknown";
  }

  /** Extracts node metadata from OWL class annotations and tracks hierarchies. */
  private void loadClass(
      OWLOntology ontology,
      OWLReasoner reasoner,
      OWLClass cls,
      EFOModel.Builder builder,
      Map<String, Set<String>> reverseSubClassOf,
      Map<String, Set<String>> reversePartOf) {

    String id = cls.getIRI().toString();
    EFONode node = new EFONode(id, null);

    // Extract annotations
    Set<OWLAnnotation> annotations = cls.getAnnotations(ontology);
    for (OWLAnnotation ann : annotations) {
      if (!(ann.getValue() instanceof OWLLiteral)) continue;

      OWLLiteral literal = (OWLLiteral) ann.getValue();
      String value = literal.getLiteral();

      if (ann.getProperty().isLabel()) {
        if (node.getTerm() == null) {
          node.setTerm(value);
        } else {
          node.getAlternativeTerms().add(value);
        }
      } else if (IRI_AE_LABEL.equals(ann.getProperty().getIRI())) {
        if (node.getTerm() != null) {
          node.getAlternativeTerms().add(node.getTerm());
        }
        node.setTerm(value);
      } else if (IRI_EFO_URI.equals(ann.getProperty().getIRI())) {
        node.setEfoUri(value);
      } else if (IRI_ALT_TERM.equals(ann.getProperty().getIRI())) {
        node.getAlternativeTerms().add(value);
      } else if (IRI_ORG_CLASS.equals(ann.getProperty().getIRI())) {
        node.setIsOrganizationalClass(Boolean.valueOf(value));
      }
    }

    if (node.getTerm() == null) {
      log.warn("No term for class [{}]", id);
    }

    builder.addNode(node);

    // Track subClassOf parents (direct only)
    NodeSet<OWLClass> superClasses = reasoner.getSuperClasses(cls, true);
    for (Node<OWLClass> superNode : superClasses) {
      String parentId = superNode.getRepresentativeElement().getIRI().toString();
      reverseSubClassOf.computeIfAbsent(id, k -> new HashSet<>()).add(parentId);
    }

    // Track part_of relations
    Set<OWLSubClassOfAxiom> subClassAxioms = ontology.getSubClassAxiomsForSubClass(cls);
    for (OWLSubClassOfAxiom axiom : subClassAxioms) {
      OWLClassExpression superClass = axiom.getSuperClass();

      if (superClass instanceof OWLObjectSomeValuesFrom) {
        OWLObjectSomeValuesFrom restriction = (OWLObjectSomeValuesFrom) superClass;

        if (IRI_PART_OF.equals(restriction.getProperty().asOWLObjectProperty().getIRI())
            && restriction.getFiller() instanceof OWLClass) {
          String partOfParentId = ((OWLClass) restriction.getFiller()).getIRI().toString();
          reversePartOf.computeIfAbsent(id, k -> new HashSet<>()).add(partOfParentId);
        }
      }
    }
  }

  /** Links child/parent bidirectional relationships in EFONode SortedSets. */
  private void linkParentsAndChildren(
      EFOModel.Builder builder, Map<String, Set<String>> reverseSubClassOf) {
    for (Map.Entry<String, Set<String>> entry : reverseSubClassOf.entrySet()) {
      String childId = entry.getKey();
      EFONode child = builder.getNodes().get(childId);
      if (child == null) continue;

      for (String parentId : entry.getValue()) {
        EFONode parent = builder.getNodes().get(parentId);
        if (parent != null) {
          child.addParent(parent);
          parent.addChild(child);
        }
      }
    }
  }

  /** Holds model + version from load(). */
  private static class LoadResult {
    final EFOModel model;
    final String version;

    LoadResult(EFOModel model, String version) {
      this.model = model;
      this.version = version;
    }
  }
}
