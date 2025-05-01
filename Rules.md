

## DAO Implementation Rule: Firestore Injection

For all DAO implementation classes that use Firestore, always follow this pattern:

- **Inject Firestore using Spring's @Autowired annotation**
- **Always import the annotation:**
  ```java
  import org.springframework.beans.factory.annotation.Autowired;
  ```
- **Example usage in a DAO:**
  ```java
  @Repository
  public class ExampleDaoImpl implements ExampleDao {
      @Autowired
      private Firestore db;
      // ... DAO methods ...
  }
  ```

This ensures a single, Spring-managed Firestore client is used throughout the application. **Do not manually initialize or close Firestore in DAOs.**