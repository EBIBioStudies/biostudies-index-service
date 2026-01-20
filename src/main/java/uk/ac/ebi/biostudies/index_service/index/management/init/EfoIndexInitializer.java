package uk.ac.ebi.biostudies.index_service.index.management.init;

import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.index.management.IndexInitializer;

@Component
public class EfoIndexInitializer implements IndexInitializer {

  /**
   * Opens the Lucene index, including its directory and any necessary resources. Implementations
   * should ensure the index is ready for read/write operations.
   *
   * @throws IllegalStateException if an I/O error occurs while opening the index
   */
  @Override
  public void open() {
    System.out.println("Opening EFO Index");
  }

  /**
   * Performs any special initialization logic needed for the index, such as creating it if missing
   * and populating from a data source. By default, this method does nothing.
   *
   * @throws IllegalStateException if an error occurs during initialization
   */
  @Override
  public void initializeIfNeeded(){
    System.out.println("Initializing EFO Index");
  }
}
