package uk.ac.ebi.biostudies.index_service.autocomplete;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Token-based trie for phrase matching.
 *
 * <p>Each path in the trie represents a normalized token sequence. Terminal nodes store canonical
 * EFO metadata so alternative labels can resolve to the same EFO ID and label.
 */
public final class TokenTrie {

  private final Node root = new Node();

  /**
   * Builds a trie from entries.
   *
   * @param entries label entries
   * @return built trie
   */
  public static TokenTrie build(Collection<Entry> entries) {
    TokenTrie trie = new TokenTrie();
    for (Entry entry : entries) {
      trie.insert(entry.label(), entry.efoId(), entry.canonicalLabel());
    }
    return trie;
  }

  /**
   * Inserts a label into the trie.
   *
   * @param label original label text
   * @param efoId canonical EFO ID
   * @param canonicalLabel label to return when this entry matches
   */
  public void insert(String label, String efoId, String canonicalLabel) {
    Objects.requireNonNull(efoId, "efoId must not be null");
    Objects.requireNonNull(canonicalLabel, "canonicalLabel must not be null");

    String[] tokens = TextNormalizer.tokenize(label);
    if (tokens.length == 0) {
      return;
    }

    Node node = root;
    for (String token : tokens) {
      node = node.children.computeIfAbsent(token, k -> new Node());
    }

    node.terminal = true;
    node.efoId = efoId;
    node.canonicalLabel = canonicalLabel;
    node.tokenCount = tokens.length;
  }

  /** Returns the root node for scanning. */
  public Node root() {
    return root;
  }

  /** Trie entry used for construction. */
  public record Entry(String label, String efoId, String canonicalLabel) {}

  /** Terminal match result. */
  public record Match(String efoId, String canonicalLabel, int startToken, int endToken) {}

  /** Trie node. */
  public static final class Node {
    private final Map<String, Node> children = new HashMap<>();
    private boolean terminal;
    private String efoId;
    private String canonicalLabel;
    private int tokenCount;

    public Node child(String token) {
      return children.get(token);
    }

    public boolean isTerminal() {
      return terminal;
    }

    public String efoId() {
      return efoId;
    }

    public String canonicalLabel() {
      return canonicalLabel;
    }

    public int tokenCount() {
      return tokenCount;
    }
  }
}
