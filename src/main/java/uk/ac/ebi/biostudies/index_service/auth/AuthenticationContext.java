package uk.ac.ebi.biostudies.index_service.auth;

public class AuthenticationContext {
  private static final ThreadLocal<User> currentUser = new ThreadLocal<>();

  /**
   * Gets the current authenticated user. Returns null if no user is authenticated. This is the
   * replacement for Session.getCurrentUser()
   */
  public static User getCurrentUser() {
    return currentUser.get();
  }

  /** Sets the current user for this request thread. Should be called by authentication filter. */
  public static void setCurrentUser(User user) {
    currentUser.set(user);
  }

  /** Clears the current user from thread local. Should be called after request completes. */
  public static void clear() {
    currentUser.remove();
  }
}
