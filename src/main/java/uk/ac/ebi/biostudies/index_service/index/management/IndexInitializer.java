package uk.ac.ebi.biostudies.index_service.index.management;

/**
 * Interface defining the lifecycle operations for a Lucene index within the system. Implementations
 * manage opening and optional special initialization of indexes.
 *
 * <p>This interface abstracts the mechanics of initializing an index, allowing for standard or
 * custom setup logic (such as creating an index from a data file if it does not exist).
 *
 * <p>Typical usage involves calling {@link #open()} to open the index directories and writers,
 * followed by {@link #initializeIfNeeded()} which by default performs no action, but can be
 * overridden for special initialization routines.
 */
public interface IndexInitializer {

  /**
   * Opens the Lucene index, including its directory and any necessary resources. Implementations
   * should ensure the index is ready for read/write operations.
   *
   * @throws IllegalStateException if an error occurs while opening the index
   */
  void open() throws IllegalStateException;

  /**
   * Performs any special initialization logic needed for the index, such as creating it if missing
   * and populating from a data source. By default, this method does nothing.
   *
   * @throws IllegalStateException if an error occurs during initialization
   */
  default void initializeIfNeeded() throws IllegalStateException {
    // Default: no special initialization needed
  }
}
